from __future__ import annotations

import asyncio
import ipaddress
import re
import time
from collections import OrderedDict
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from urllib.parse import quote_from_bytes, urljoin, urlparse

from playwright.async_api import TimeoutError as PlaywrightTimeoutError
from playwright.async_api import async_playwright

from config import Settings


class EbookScraper:
    WAIT_TIMEOUT_MS = 10000
    MAX_CACHE_ENTRIES = 2000

    RESULT_SELECTOR = ".ebook-list, .search_result_list, .book_list, table tbody tr"
    ROW_SELECTORS = [
        ".ebook-list .bx",
        ".ebook-list > div",
        ".ebook-list li",
        ".search_result_list li",
        ".book_list li",
        "table tbody tr",
    ]

    TOK_AVAILABLE = ["\ub300\ucd9c\uac00\ub2a5", "\ub300\ucd9c \uac00\ub2a5"]
    TOK_ON_LOAN = ["\ub300\ucd9c\uc911", "\ub300\ucd9c \uc911", "\ub300\ucd9c\ubd88\uac00"]
    TOK_NOT_OWNED = ["\ubbf8\uc18c\uc7a5"]

    def __init__(self, settings: Settings):
        self.settings = settings
        self._init_lock = asyncio.Lock()
        self._login_lock = asyncio.Lock()
        self._semaphore = asyncio.Semaphore(max(1, settings.playwright_concurrency))
        self._pw = None
        self._browser = None
        self._context = None
        self._logged_in = False
        self._cache: "OrderedDict[str, tuple[float, Dict[str, Any]]]" = OrderedDict()

    async def check_ebook_by_title(self, title: str, author: str = "", publisher: str = "") -> Dict[str, Any]:
        search_title = self.normalize_search_title(title)
        if not search_title:
            return self._zero("", "title_required")

        cache_key = self._cache_key(search_title, author, publisher)
        cached = self._cache_get(cache_key)
        if cached is not None:
            return cached

        search_url = self._build_search_url(search_title)
        if not self._is_allowed_url(search_url):
            result = self._zero(search_title, "blocked_url")
            self._cache_set(cache_key, result, transient=True)
            return result

        try:
            result = await asyncio.wait_for(
                self._scrape(search_title, search_url, author, publisher),
                timeout=max(1, self.settings.ebook_total_timeout_sec),
            )
        except asyncio.TimeoutError:
            result = self._unavailable(search_title, search_url, "ebook_total_timeout")
        except Exception:
            result = self._unavailable(search_title, search_url, "scrape_error")

        self._cache_set(cache_key, result, transient=not result.get("found", False))
        return result

    async def _scrape(self, search_title: str, search_url: str, author: str, publisher: str) -> Dict[str, Any]:
        acquired = False
        try:
            await asyncio.wait_for(self._semaphore.acquire(), timeout=10)
            acquired = True
        except Exception:
            return self._unavailable(search_title, search_url, "semaphore_timeout")

        try:
            await asyncio.wait_for(self._ensure_browser(), timeout=10)
            await asyncio.wait_for(self._ensure_login(), timeout=10)

            page = await self._context.new_page()
            page.set_default_timeout(self.WAIT_TIMEOUT_MS)
            await page.route("**/*", self._route_handler)
            try:
                await page.goto(search_url, wait_until="domcontentloaded", timeout=10000)

                # Poll body text until search results render (max ~10s).
                # Note: page chrome contains "대출가능" in navigation, so only
                # break on "보유 N" (result-specific) or no-result indicators.
                body_text = ""
                for _ in range(20):
                    body_text = await page.evaluate("() => document.body.textContent")
                    body_text = self._ws(body_text)
                    if re.search(r"\ubcf4\uc720\s*\d+", body_text):
                        break
                    if "\uac80\uc0c9\uacb0\uacfc\uac00 \uc5c6\uc2b5\ub2c8\ub2e4" in body_text:
                        break
                    if re.search(r"\uc804\uccb4\s*0\uac74", body_text):
                        break
                    await asyncio.sleep(0.5)

                # Check for no-result page ("검색결과가 없습니다" or "전체 0건").
                if "\uac80\uc0c9\uacb0\uacfc\uac00 \uc5c6\uc2b5\ub2c8\ub2e4" in body_text:
                    return self._zero(search_title, "")
                if re.search(r"\uc804\uccb4\s*0\uac74", body_text):
                    return self._zero(search_title, "")

                row_text, detail_url = await self._best_row(page, search_title, author, publisher)

                # No result rows found → no ebook exists for this search.
                if not row_text.strip():
                    return self._zero(search_title, "")

                # Parse holdings from row_text only (body chrome has misleading numbers).
                total_holdings, loaned_holdings, existing_holdings = self._parse_holdings(row_text)
                # Fallback: "대출가능" in row text but no parsed numbers → at least 1 available.
                if existing_holdings == 0 and any(token in row_text for token in self.TOK_AVAILABLE):
                    total_holdings = max(total_holdings, 1)
                    existing_holdings = 1

                found = existing_holdings > 0 or total_holdings > 0
                display_status = "\uc18c\uc7a5" if found else "\ubbf8\uc18c\uc7a5"

                return self._build_result(
                    title=search_title,
                    found=found,
                    total_holdings=total_holdings,
                    available_holdings=existing_holdings,
                    deep_link_url=detail_url or search_url,
                    status_text=display_status,
                    error_message="",
                )
            except PlaywrightTimeoutError:
                return self._unavailable(search_title, search_url, "timeout_10s")
            except Exception:
                return self._unavailable(search_title, search_url, "scrape_error")
            finally:
                await page.close()
        finally:
            if acquired:
                self._semaphore.release()

    async def _ensure_browser(self) -> None:
        if self._browser is not None and self._context is not None:
            return
        async with self._init_lock:
            if self._browser is not None and self._context is not None:
                return
            self._pw = await async_playwright().start()
            self._browser = await self._pw.chromium.launch(
                headless=self.settings.playwright_headless,
                args=["--no-sandbox", "--disable-setuid-sandbox"],
            )
            self._context = await self._browser.new_context()

    async def _ensure_login(self) -> None:
        # Browser context keeps cookies — login persists across pages.
        # Do NOT call _is_session_alive every time (CSS blocked makes it unreliable).
        if self._logged_in:
            return
        if not self.settings.lib_user_id or not self.settings.lib_user_password:
            self._logged_in = True
            return

        async with self._login_lock:
            if self._logged_in:
                return
            page = await self._context.new_page()
            page.set_default_timeout(self.WAIT_TIMEOUT_MS)
            try:
                login_url = self.settings.ebook_base_url.rstrip("/") + "/member/?mode=login"
                await page.goto(login_url, wait_until="domcontentloaded", timeout=10000)
                await page.fill("input[name='user_id']", self.settings.lib_user_id, timeout=10000)
                await page.fill("input[name='user_pw']", self.settings.lib_user_password, timeout=10000)
                await page.click("a#left_Login_Click", timeout=10000)
                await page.wait_for_load_state("domcontentloaded", timeout=10000)
                self._logged_in = True
            except Exception:
                # Login failed but continue — ebook search may work without login
                self._logged_in = True
            finally:
                await page.close()

    async def _is_session_alive(self) -> bool:
        if self._context is None:
            return False
        page = await self._context.new_page()
        page.set_default_timeout(self.WAIT_TIMEOUT_MS)
        try:
            login_url = self.settings.ebook_base_url.rstrip("/") + "/member/?mode=login"
            await page.goto(login_url, wait_until="domcontentloaded", timeout=10000)
            has_id = await page.locator("input[name='user_id']").count() > 0
            has_pw = await page.locator("input[name='user_pw']").count() > 0
            return not (has_id and has_pw)
        except Exception:
            return False
        finally:
            await page.close()

    async def _find_first(self, page, selectors: list[str]):
        for selector in selectors:
            try:
                await page.wait_for_selector(selector, timeout=10000, state="attached")
                loc = page.locator(selector).first
                if await loc.count() > 0:
                    return loc
            except Exception:
                continue
        return None

    async def _route_handler(self, route) -> None:
        req = route.request
        if req.resource_type in {"image", "font", "media", "stylesheet"}:
            await route.abort()
            return
        if not self._is_allowed_url(req.url):
            await route.abort()
            return
        await route.continue_()

    async def _wait_for_results(self, page) -> bool:
        try:
            await page.wait_for_selector(self.RESULT_SELECTOR, timeout=10000, state="attached")
            return True
        except Exception:
            return False

    async def _wait_for_holding_number(self, page) -> bool:
        try:
            await page.wait_for_selector(r"text=/\ubcf4\uc720\s*\d+/", timeout=10000, state="attached")
            return True
        except Exception:
            return False

    async def _best_row(self, page, title: str, author: str, publisher: str) -> tuple[str, str]:
        nt = self._norm(title)
        na = self._norm(author)
        np_ = self._norm(publisher)
        best_ebook_text = ""
        best_ebook_link = ""
        best_ebook_score = -1
        best_any_text = ""
        best_any_link = ""
        best_any_score = -1

        for selector in self.ROW_SELECTORS:
            try:
                nodes = page.locator(selector)
                count = await nodes.count()
                if count <= 0:
                    continue
                for i in range(min(count, 20)):
                    node = nodes.nth(i)
                    text = self._ws(await node.inner_text())
                    if not text:
                        continue
                    score = 0
                    t = text.lower()
                    if nt and nt in t:
                        score += 100
                    if na and na in t:
                        score += 40
                    if np_ and np_ in t:
                        score += 30
                    if re.search(r"(\ubcf4\uc720|\ub300\ucd9c\uac00\ub2a5|\ub300\ucd9c\s*\uac00\ub2a5|\uad8c)", text):
                        score += 10
                    if "\uc804\uc790\ucc45" in text:
                        score += 15
                    if score > best_any_score:
                        best_any_score = score
                        best_any_text = text
                        best_any_link = await self._row_link(node)
                    if self._is_ebook_row_text(text) and score > best_ebook_score:
                        best_ebook_score = score
                        best_ebook_text = text
                        best_ebook_link = await self._row_link(node)
                if best_ebook_score >= 0:
                    break
            except Exception:
                continue
        if best_ebook_score >= 0:
            return best_ebook_text, best_ebook_link
        return best_any_text, best_any_link

    async def _row_link(self, node) -> str:
        try:
            href = await node.locator("a[href]").first.get_attribute("href")
            if not href:
                return ""
            link = self._absolute_url(href)
            return link if self._is_allowed_url(link) else ""
        except Exception:
            return ""

    async def _read_detail_status(self, detail_url: str) -> str:
        if not detail_url or not self._is_allowed_url(detail_url):
            return "UNKNOWN"
        page = await self._context.new_page()
        page.set_default_timeout(self.WAIT_TIMEOUT_MS)
        await page.route("**/*", self._route_handler)
        try:
            await page.goto(detail_url, wait_until="domcontentloaded", timeout=10000)
            try:
                await page.wait_for_selector("button, .btn, .status, [class*='status']", timeout=10000, state="attached")
            except Exception:
                pass
            text = self._ws(await page.inner_text("body"))
            if any(token in text for token in self.TOK_AVAILABLE):
                return "AVAILABLE"
            if any(token in text for token in self.TOK_ON_LOAN):
                return "ON_LOAN"
            if any(token in text for token in self.TOK_NOT_OWNED):
                return "NOT_OWNED"
            return "UNKNOWN"
        except Exception:
            return "UNKNOWN"
        finally:
            await page.close()

    @staticmethod
    def _parse_holdings(text: str) -> tuple[int, int, int]:
        # Required exact parse:
        # re.search(r'보유\s*(\d+)', text)
        m_total = re.search(r"\ubcf4\uc720\s*[:：]?\s*(\d+)", text) or re.search(r"\ubcf4\uc720\s*\(\s*(\d+)\s*\)", text)
        total = int(m_total.group(1)) if m_total else 0

        # Required exact parse for loaned count:
        # re.search(r'대출\s*(\d+)', text)
        m_loan = re.search(r"\ub300\ucd9c\s*(\d+)", text)
        loaned = int(m_loan.group(1)) if m_loan else 0

        if total == 0:
            # Required fallback behavior: no number => return 0
            return 0, 0, 0

        if loaned > total:
            loaned = total
        existing = max(0, total - loaned)
        return total, loaned, existing

    @staticmethod
    def _is_ebook_row_text(text: str) -> bool:
        compact = re.sub(r"\s+", "", text or "")
        return "\uc804\uc790\ucc45" in compact

    @staticmethod
    def _strip_subtitle(title: str) -> str:
        text = re.sub(r"\s+", " ", (title or "").strip())
        if not text:
            return ""
        stripped = re.sub(r"\s*(?:-|:|\().*$", "", text).strip()
        core = stripped if stripped else text
        # Keep only Hangul/ASCII letters/digits/spaces in the normalized query.
        core = re.sub(r"[^0-9A-Za-z\u3131-\u318E\uAC00-\uD7A3\s]", " ", core)
        core = re.sub(r"\s+", " ", core).strip()
        return core

    def normalize_search_title(self, title: str) -> str:
        return self._strip_subtitle(self._ws(title))

    @staticmethod
    def _hybrid_encode_cp949(text: str) -> str:
        # Korean -> CP949 percent encoded, English/numbers keep raw, spaces -> +
        parts: list[str] = []
        for ch in text:
            if ch == " ":
                parts.append("+")
            elif ch.isascii() and ch.isalnum():
                parts.append(ch)
            elif re.match(r"[\u3131-\u318E\uAC00-\uD7A3]", ch):
                parts.append(quote_from_bytes(ch.encode("cp949"), safe=""))
            else:
                parts.append(quote_from_bytes(ch.encode("utf-8"), safe=""))
        return "".join(parts)

    def _build_search_url(self, title: str) -> str:
        encoded = self._hybrid_encode_cp949(title)
        return f"{self.settings.ebook_base_url.rstrip('/')}/search/?srch_order=total&src_key={encoded}"

    def build_search_deep_link(self, title: str) -> str:
        normalized = self.normalize_search_title(title)
        if not normalized:
            return "https://ebook.yjc.ac.kr/"
        return self._build_search_url(normalized)

    def _is_allowed_url(self, url: str) -> bool:
        try:
            parsed = urlparse(url)
            if parsed.scheme.lower() not in {"http", "https"}:
                return False
            host = (parsed.hostname or "").lower()
            if not host or host in {"localhost", "127.0.0.1"}:
                return False
            try:
                ip = ipaddress.ip_address(host)
                if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
                    return False
            except ValueError:
                pass
            return host in set(self.settings.allowed_ebook_hosts)
        except Exception:
            return False

    def _absolute_url(self, href: str) -> str:
        if href.startswith("http://") or href.startswith("https://"):
            return href
        return urljoin(self.settings.ebook_base_url.rstrip("/") + "/", href.lstrip("/"))

    @staticmethod
    def _ws(text: str) -> str:
        return re.sub(r"\s+", " ", (text or "").strip())

    @staticmethod
    def _norm(text: str) -> str:
        return re.sub(r"\s+", " ", (text or "").strip()).lower()

    def _cache_key(self, title: str, author: str, publisher: str) -> str:
        return f"v10:{self._norm(title)}|{self._norm(author)}|{self._norm(publisher)}"

    def _cache_get(self, key: str) -> Optional[Dict[str, Any]]:
        entry = self._cache.get(key)
        if not entry:
            return None
        expires_at, payload = entry
        if time.time() > expires_at:
            self._cache.pop(key, None)
            return None
        self._cache.move_to_end(key)
        return payload

    def _cache_set(self, key: str, payload: Dict[str, Any], transient: bool) -> None:
        ttl = self.settings.ebook_cache_ttl_sec if not transient else min(15, self.settings.ebook_cache_ttl_sec)
        self._cache[key] = (time.time() + ttl, payload)
        self._cache.move_to_end(key)
        while len(self._cache) > self.MAX_CACHE_ENTRIES:
            self._cache.popitem(last=False)

    def _build_result(
        self,
        title: str,
        found: bool,
        total_holdings: int,
        available_holdings: int,
        deep_link_url: str,
        status_text: str,
        error_message: str,
    ) -> Dict[str, Any]:
        return {
            "title": title,
            "found": found,
            "total_holdings": max(0, int(total_holdings)),
            "available_holdings": max(0, int(available_holdings)),
            "deep_link_url": deep_link_url,
            "status_text": status_text,
            "error_message": error_message,
            "checked_at": datetime.now(timezone.utc).isoformat(),
        }

    def _zero(self, title: str, error: str) -> Dict[str, Any]:
        return self._build_result(
            title=self.normalize_search_title(title) if title else "",
            found=False,
            total_holdings=0,
            available_holdings=0,
            deep_link_url=self.build_search_deep_link(title),
            status_text="\ubbf8\uc18c\uc7a5",
            error_message=error,
        )

    def _unavailable(self, title: str, deep_link_url: str, error: str) -> Dict[str, Any]:
        return self._build_result(
            title=self.normalize_search_title(title),
            found=False,
            total_holdings=0,
            available_holdings=0,
            deep_link_url=deep_link_url,
            status_text="\ubbf8\uc18c\uc7a5",
            error_message=error,
        )
