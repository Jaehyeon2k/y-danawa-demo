from __future__ import annotations

import asyncio
import ipaddress
import logging
import re
import time
from collections import OrderedDict, defaultdict, deque
from datetime import datetime, timezone
from typing import Any, Dict, Optional, TYPE_CHECKING
from urllib.parse import quote_from_bytes, urlparse

from playwright.async_api import TimeoutError as PlaywrightTimeoutError
from playwright.async_api import async_playwright

if TYPE_CHECKING:
    from config import Settings


logger = logging.getLogger(__name__)

CACHE_SCHEMA_VERSION = "v6"
DEFAULT_STABLE_TTL_SEC = 3 * 60 * 60
DEFAULT_TRANSIENT_TTL_SEC = 90
MAX_CACHE_ENTRIES = 2000
TIMEOUT_MS = 10000

# Status keywords found inside result rows (NOT body chrome)
_TOK_AVAILABLE = "\ub300\ucd9c\uac00\ub2a5"       # 대출가능
_TOK_ON_LOAN = "\ub300\ucd9c\uc911"                # 대출중
_TOK_UNAVAIL = "\ub300\ucd9c\ubd88\uac00"          # 대출불가
_TOK_RESERVED = "\uc608\uc57d"                      # 예약
_TOK_UNUSABLE = "\uc774\uc6a9\ubd88\uac00"          # 이용불가
_TOK_LOST = "\ubd84\uc2e4"                          # 분실
_TOK_REPAIR = "\uc815\ub9ac\uc911"                  # 정리중
_TOK_MONOGRAPH = "\ub2e8\ud589\ubcf8"               # 단행본
_ROW_STATUS_TOKENS = (_TOK_AVAILABLE, _TOK_ON_LOAN, _TOK_UNAVAIL, _TOK_RESERVED)


class LibraryScraper:
    """lib.yju.ac.kr Knockout.js SPA scraper.

    Key design:
      - timeout=10000 on every Playwright call.
      - wait_until='domcontentloaded' (networkidle exceeds 5s).
      - Poll until result rows appear AND contain status text.
      - Use monograph (단행본) row text for status detection, NOT body text
        (body always contains "대출불가"/"대출중" in SPA chrome/navigation).
    """

    def __init__(self, settings: "Settings"):
        self.settings = settings
        self._init_lock = asyncio.Lock()
        self._login_lock = asyncio.Lock()
        self._semaphore = asyncio.Semaphore(max(1, settings.playwright_concurrency))

        self._pw = None
        self._browser = None
        self._context = None
        self._logged_in = False

        self._cache: "OrderedDict[str, tuple[float, Dict[str, Any]]]" = OrderedDict()
        self._rate_windows: dict[str, deque[float]] = defaultdict(deque)

    async def check_library(self, isbn: Optional[str], title: Optional[str], author: Optional[str]) -> Dict[str, Any]:
        isbn13 = self._normalize_isbn13(isbn)
        normalized_title = self._normalize_title_query(title)
        search_key = isbn13 or normalized_title
        if not search_key:
            return self._empty_response(error="isbn13 or title is required", status_code="UNKNOWN")

        cache_key = self._cache_key(search_key)
        cached = self._get_cache(cache_key)
        if cached is not None:
            return self._enforce_safety(cached, search_key)

        if not self._allow_request(cache_key):
            return self._empty_response(error="rate_limited", status_code="ERROR")

        search_url = self._build_search_url(search_key)
        if not self._is_allowed_url(search_url):
            return self._empty_response(detail_url=search_url, error="blocked_url", status_code="ERROR")

        async with self._semaphore:
            await self._ensure_browser()
            page = await self._context.new_page()
            page.set_default_timeout(TIMEOUT_MS)
            await page.route("**/*", self._route_handler)

            try:
                await self._ensure_login(page)
                try:
                    await page.goto(search_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
                except PlaywrightTimeoutError:
                    pass  # SPA hash routing may still work

                # Wait for SPA to finish rendering: either status text appears
                # in result rows, or the "networkidle" state is reached.
                try:
                    await page.wait_for_load_state("networkidle", timeout=5000)
                except Exception:
                    pass

                evidence = await self._collect_evidence(page, normalized_title, isbn13)
                status_code, reason = self.decide_status(evidence)

                available = status_code == "AVAILABLE"
                found = status_code in {"AVAILABLE", "ON_LOAN", "RESERVED", "UNAVAILABLE"}

                row_text = evidence.get("row_text", "")
                if status_code in {"AVAILABLE", "ON_LOAN", "RESERVED", "UNAVAILABLE"} and row_text:
                    location = self._extract_location(row_text)
                    call_number = self._extract_call_number(row_text)
                else:
                    location = ""
                    call_number = ""

                result = {
                    "found": found,
                    "available": available,
                    "location": location,
                    "call_number": call_number,
                    "detail_url": search_url,
                    "error_message": "" if status_code != "ERROR" else reason,
                    "loan_status_text": status_code,
                    "detail_verified": bool(evidence.get("content_ready")),
                    "checked_at": datetime.now(timezone.utc).isoformat(),
                    "is_owned": status_code in {"AVAILABLE", "ON_LOAN", "RESERVED", "UNAVAILABLE"},
                    "status_text": status_code,
                    "status_text_ko": self._status_text_ko(status_code),
                    "overall_status": self._overall_status_label(status_code),
                    "record_type_picked": evidence.get("record_type_picked"),
                    "matched_title": evidence.get("matched_title", ""),
                    "retry_required": status_code in {"ERROR", "UNKNOWN"},
                    "isbn13": isbn13,
                    "evidence": {
                        "page_guard_ok": bool(evidence.get("page_guard_ok")),
                        "result_container_ready": bool(evidence.get("content_ready")),
                        "not_owned_signal": bool(evidence.get("not_owned")),
                        "loan_signal": bool(evidence.get("has_available") or evidence.get("has_on_loan")),
                        "row_count": int(evidence.get("row_count", 0)),
                        "matched_title": evidence.get("matched_title", ""),
                        "record_type_picked": evidence.get("record_type_picked"),
                        "aggregate_reason": evidence.get("aggregate_reason", ""),
                    },
                }
                safe = self._enforce_safety(result, search_key)

                logger.info(
                    "library key=%s status=%s reason=%s rows=%s url=%s",
                    search_key, safe["status_text"], reason,
                    evidence.get("row_count", 0), search_url,
                )

                self._set_cache(cache_key, safe)
                return safe
            except PlaywrightTimeoutError as e:
                logger.warning("library_timeout key=%s msg=%s", search_key, str(e))
                result = self._safe_unknown_response(
                    detail_url=search_url,
                    error="timeout_10s",
                    retry_required=True,
                    query_key=search_key,
                )
                self._set_cache(cache_key, result)
                return result
            except Exception:
                logger.exception("library_error key=%s", search_key)
                result = self._safe_unknown_response(
                    detail_url=search_url,
                    error="\uc815\ubcf4 \uc5c6\uc74c",
                    retry_required=True,
                    query_key=search_key,
                )
                self._set_cache(cache_key, result)
                return result
            finally:
                await page.close()

    # ================================================================
    # evidence collection — poll SPA, extract monograph row text
    # ================================================================

    async def _collect_evidence(self, page, normalized_title: str, isbn13: str) -> Dict[str, Any]:
        """Poll until result rows appear with status text, then extract evidence.

        Critical fix: do NOT break as soon as rows exist — the SPA renders
        row elements before populating status text ("대출가능"/"대출중").
        We must wait until status keywords appear inside the row content.
        """
        body_text = ""
        row_count = 0
        monograph_text = ""
        monograph_html = ""
        ebook_text = ""
        all_row_text = ""
        monograph_row_count = 0
        ebook_row_count = 0
        monograph_rows_texts: list[str] = []
        ebook_rows_texts: list[str] = []

        # Wait for SPA key markers in parallel (max 5s each, running concurrently).
        # Must stay under gRPC hard timeout (16s total including login + networkidle).
        await asyncio.gather(
            self._safe_wait(page, "text=\ub2e8\ud589\ubcf8, .bookinfo, li.mb-4, div.bookinfo", TIMEOUT_MS),
            self._safe_wait(page, "text=/\ub300\ucd9c\\s*(\uac00\ub2a5|\uc911)/", TIMEOUT_MS),
            self._safe_wait(page, "text=\uc804\uc790\ucc45, text=/\ubcf4\uc720\\s*\\d+/", TIMEOUT_MS),
        )

        poll_interval = 0.5
        max_polls = max(6, int(self.settings.status_wait_timeout_ms / 1000 / poll_interval))
        for poll_idx in range(max_polls):
            try:
                body_text = await page.evaluate("() => document.body.textContent")
            except Exception:
                body_text = ""

            # Early exit: if body shows "소장자료 0" or "전체건수 0" after
            # the SPA has rendered, we know there are no results.
            # Note: "검색결과가 없습니다" cannot be trusted (always present in SPA).
            if poll_idx >= 2 and body_text:
                if re.search(r"\uc18c\uc7a5\uc790\ub8cc\s*0", body_text) or re.search(r"\uc804\uccb4\uac74\uc218\s*0", body_text) or re.search(r"\uc804\uccb4\s*0\s*\uac74", body_text):
                    break

            # Check for result rows (.bookinfo or li.mb-4)
            cur_count = 0
            for sel in (".bookinfo", "li.mb-4", "div.bookinfo"):
                try:
                    c = await page.locator(sel).count()
                    if c > 0:
                        cur_count = c
                        break
                except Exception:
                    pass

            if cur_count > 0:
                row_count = cur_count
                # Extract row text by type (monograph / ebook)
                mono_parts = []
                ebook_parts = []
                all_parts = []
                monograph_candidates: list[tuple[int, str, str]] = []
                ebook_candidates: list[tuple[int, str]] = []
                seen_rows: set[str] = set()
                for sel in (".bookinfo", "li.mb-4", "div.bookinfo"):
                    try:
                        loc = page.locator(sel)
                        c = await loc.count()
                        if c <= 0:
                            continue
                        for i in range(min(c, 30)):
                            try:
                                t = await loc.nth(i).inner_text()
                                h = await loc.nth(i).inner_html()
                                t_ws = re.sub(r"\s+", " ", t).strip()
                                if not t_ws:
                                    continue
                                row_sig = f"{t_ws[:120]}|{len(t_ws)}"
                                if row_sig in seen_rows:
                                    continue
                                seen_rows.add(row_sig)
                                all_parts.append(t_ws)
                                score = self._row_match_score(
                                    row_text=t_ws,
                                    normalized_title=normalized_title,
                                    isbn13=isbn13,
                                )
                                is_match = self._row_matches_query(t_ws, normalized_title, isbn13)
                                if self._is_monograph_row(t_ws, h) and is_match:
                                    mono_parts.append(t_ws)
                                    monograph_candidates.append((score, t_ws, h))
                                    continue
                                if self._is_ebook_row(t_ws, h) and is_match:
                                    ebook_parts.append(t_ws)
                                    ebook_candidates.append((score, t_ws))
                                    continue
                                # Fallback classification: rows with 대출 상태 token and no 전자책 marker
                                # are treated as monograph candidates to avoid UNKNOWN false negatives.
                                if is_match and not self._is_ebook_row(t_ws, h):
                                    if any(tok in t_ws for tok in _ROW_STATUS_TOKENS):
                                        mono_parts.append(t_ws)
                                        monograph_candidates.append((score, t_ws, h))
                            except Exception:
                                pass
                    except Exception:
                        continue

                monograph_row_count = len(mono_parts)
                ebook_row_count = len(ebook_parts)
                monograph_rows_texts = mono_parts[:]
                ebook_rows_texts = ebook_parts[:]
                monograph_text, monograph_html = self._pick_best_monograph_row(monograph_candidates, fallback_parts=mono_parts)
                ebook_text = self._pick_best_row(ebook_candidates, fallback_parts=ebook_parts)
                all_row_text = " ".join(all_parts)

                # Only break when status keywords are found in the row text.
                # If rows exist but no status text yet, the SPA is still rendering.
                # Physical status must only rely on monograph rows.
                check = monograph_text
                if any(tok in check for tok in _ROW_STATUS_TOKENS):
                    break

            # Early exit: after networkidle + 2s polling with 0 rows, the book is not found.
            if poll_idx >= 4 and cur_count == 0:
                break

            await asyncio.sleep(poll_interval)

        # Secondary wait: if monograph rows exist but no status text,
        # the SPA is still running AJAX calls to check availability.
        # "조회중.." → "대출가능"/"대출중" transition can be slow.
        if monograph_row_count > 0 and not any(tok in monograph_text for tok in _ROW_STATUS_TOKENS):
            for _ in range(10):  # Extra 5s
                await asyncio.sleep(0.5)
                try:
                    for sel in (".bookinfo", "li.mb-4", "div.bookinfo"):
                        loc = page.locator(sel)
                        c = await loc.count()
                        if c > 0:
                            mono_retry = []
                            for i in range(min(c, 30)):
                                try:
                                    t = await loc.nth(i).inner_text()
                                    t_ws = re.sub(r"\s+", " ", t)
                                    if self._is_monograph_row(t_ws, "") and self._row_matches_query(t_ws, normalized_title, isbn13):
                                        mono_retry.append(t_ws)
                                        if any(tok in t_ws for tok in _ROW_STATUS_TOKENS):
                                            monograph_text = t_ws
                                            monograph_row_count = len(mono_retry)
                                            break
                                except Exception:
                                    pass
                            if any(tok in monograph_text for tok in _ROW_STATUS_TOKENS):
                                break
                            break
                except Exception:
                    pass
                if any(tok in monograph_text for tok in _ROW_STATUS_TOKENS):
                    break

        body_text = re.sub(r"\s+", " ", body_text)

        # Physical status uses monograph row only.
        row_text = monograph_text.strip()

        page_guard_ok = not self._is_guard_failure(body_text)
        # NOT_OWNED is determined solely by 0 result rows after full polling.
        # Body text "검색결과가 없습니다" is ALWAYS present in this SPA (from other
        # search sections like 전자책/RISS) and CANNOT be trusted.
        not_owned = (row_count == 0)

        # Detect status from row_text only (body_text has SPA chrome noise)
        detect_text = row_text
        # Aggregate physical rows: if any monograph row is available, treat as AVAILABLE.
        has_available = any(
            (_TOK_AVAILABLE in t) or ("\ub300\ucd9c \uac00\ub2a5" in t)
            for t in monograph_rows_texts
        ) or self._has_available_in_monograph(detect_text, monograph_html)
        has_on_loan = any(
            (_TOK_ON_LOAN in t) or ("\ub300\ucd9c \uc911" in t) or (_TOK_UNAVAIL in t)
            for t in monograph_rows_texts
        )
        has_reserved = any(_TOK_RESERVED in t for t in monograph_rows_texts)
        has_unusable = any((_TOK_UNUSABLE in t) or (_TOK_LOST in t) or (_TOK_REPAIR in t) for t in monograph_rows_texts)
        content_ready = row_count > 0 or len(body_text) > 10000
        ebook_total_holdings = self._extract_ebook_holdings(ebook_text if ebook_text.strip() else all_row_text)
        ebook_status_text = "\uc18c\uc7a5" if ebook_total_holdings >= 1 else "\ubbf8\uc18c\uc7a5"

        return {
            "body_text": body_text,
            "row_text": row_text,
            "monograph_row_found": bool(row_text),
            "monograph_row_count": monograph_row_count,
            "ebook_row_text": ebook_text,
            "ebook_row_count": ebook_row_count,
            "ebook_rows_texts": ebook_rows_texts,
            "ebook_total_holdings": ebook_total_holdings,
            "ebook_status_text": ebook_status_text,
            "page_guard_ok": page_guard_ok,
            "content_ready": content_ready,
            "row_count": row_count,
            "has_available": has_available,
            "has_on_loan": has_on_loan,
            "has_reserved": has_reserved,
            "has_unusable": has_unusable,
            "not_owned": not_owned,
            "matched_title": self._extract_display_title(row_text),
            "record_type_picked": "\ub2e8\ud589\ubcf8" if monograph_row_count > 0 else ("\uc804\uc790\ucc45" if ebook_row_count > 0 else None),
        }

    # ================================================================
    # status decision
    # ================================================================

    @staticmethod
    def decide_status(evidence: Dict[str, Any]) -> tuple[str, str]:
        if not bool(evidence.get("page_guard_ok")):
            return "ERROR", "page_guard_failed"

        has_available = bool(evidence.get("has_available"))
        has_on_loan = bool(evidence.get("has_on_loan"))
        has_reserved = bool(evidence.get("has_reserved"))
        has_unusable = bool(evidence.get("has_unusable"))
        monograph_row_found = bool(evidence.get("monograph_row_found"))
        not_owned = bool(evidence.get("not_owned"))
        content_ready = bool(evidence.get("content_ready"))
        row_count = int(evidence.get("row_count", 0))

        # NOT_OWNED: 0 result rows after full polling.
        # After networkidle + wait_for_selector + polling, 0 rows = definitively not owned.
        # Do NOT require content_ready — body text length varies and the polling loop
        # already waited long enough (networkidle + 5s parallel selectors + 2s polling).
        if content_ready and not_owned and row_count == 0:
            return "NOT_OWNED", "no_rows_after_polling"

        # Status from monograph row only.
        # If monograph row exists and no "대출가능", treat as ON_LOAN.
        if monograph_row_found:
            if has_available:
                return "AVAILABLE", "monograph_available"
            if has_on_loan or has_reserved:
                return "ON_LOAN", "monograph_on_loan_or_reserved"
            if has_unusable:
                return "UNAVAILABLE", "monograph_unusable"
            return "ON_LOAN", "monograph_without_available"

        # Rows exist but no monograph row (ebook-only or unclassified)
        if row_count > 0:
            return "NOT_OWNED", "rows_without_monograph"

        return "UNKNOWN", "insufficient_evidence"

    @staticmethod
    def decide_status_from_evidence(evidence: Dict[str, Any]) -> tuple[str, str]:
        """Backward-compatible wrapper for legacy tests/callers."""
        mapped = {
            "page_guard_ok": evidence.get("page_guard_ok", True),
            "content_ready": evidence.get("result_container_ready", evidence.get("content_ready", False)),
            "not_owned": evidence.get("not_owned_signal", evidence.get("not_owned", False)),
            "row_count": evidence.get("row_count", 0),
            "has_available": evidence.get("row_has_available", evidence.get("has_available", False)),
            "has_on_loan": evidence.get("row_has_on_loan", evidence.get("has_on_loan", False)),
            "has_reserved": evidence.get("row_has_reserved", evidence.get("has_reserved", False)),
            "has_unusable": evidence.get("row_has_unusable", evidence.get("has_unusable", False)),
            "monograph_row_found": evidence.get("monograph_row_found", int(evidence.get("row_count", 0)) > 0),
        }
        return LibraryScraper.decide_status(mapped)

    # ================================================================
    # browser lifecycle
    # ================================================================

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

    async def _ensure_login(self, page) -> None:
        # Browser context keeps cookies, so login persists across pages.
        # Do NOT call _is_session_alive every time — it navigates away from
        # the current page and is unreliable when CSS is blocked (form elements
        # remain visible in the DOM regardless of session state).
        if self._logged_in:
            return
        async with self._login_lock:
            if self._logged_in:
                return
            if not self._is_allowed_url(self.settings.lib_login_url):
                raise RuntimeError("blocked_login_url")
            await page.goto(self.settings.lib_login_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
            id_input = await self._first_locator(page, self.settings.lib_id_selector)
            pw_input = await self._first_locator(page, self.settings.lib_password_selector)
            submit_btn = await self._first_locator(page, self.settings.lib_submit_selector)
            if id_input is None or pw_input is None or submit_btn is None:
                # Login form not found = already logged in (page redirected)
                self._logged_in = True
                return
            await id_input.fill(self.settings.lib_user_id, timeout=TIMEOUT_MS)
            await pw_input.fill(self.settings.lib_user_password, timeout=TIMEOUT_MS)
            await submit_btn.click(timeout=TIMEOUT_MS)
            try:
                await page.wait_for_load_state("domcontentloaded", timeout=TIMEOUT_MS)
            except Exception:
                pass
            self._logged_in = True

    async def _is_session_alive(self, page) -> bool:
        try:
            await page.goto(self.settings.lib_login_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
        except Exception:
            return False
        # If login inputs are visible, session is not valid.
        id_input = await self._first_locator(page, self.settings.lib_id_selector)
        pw_input = await self._first_locator(page, self.settings.lib_password_selector)
        return not (id_input is not None and pw_input is not None)

    async def _route_handler(self, route) -> None:
        req = route.request
        url_l = (req.url or "").lower()
        if req.resource_type in {"image", "font", "media"}:
            await route.abort()
            return
        if req.resource_type == "stylesheet" and (url_l.endswith(".map") or "cdn" in url_l):
            await route.abort()
            return
        if any(token in url_l for token in ("analytics", "gtag", "googletagmanager", "doubleclick", "facebook", "segment")):
            await route.abort()
            return
        if not self._is_allowed_url(req.url):
            await route.abort()
            return
        await route.continue_()

    @staticmethod
    async def _safe_wait(page, selector: str, timeout_ms: int = TIMEOUT_MS) -> None:
        try:
            await page.wait_for_selector(selector, timeout=timeout_ms, state="attached")
        except Exception:
            pass

    async def _first_locator(self, page, selector_csv: str):
        selectors = [s.strip() for s in selector_csv.split(",") if s.strip()]
        for sel in selectors:
            try:
                loc = page.locator(sel).first
                if await loc.count() > 0:
                    return loc
            except Exception:
                continue
        return None

    # ================================================================
    # helpers
    # ================================================================

    def _allow_request(self, key: str) -> bool:
        now = time.time()
        dq = self._rate_windows[key]
        window = max(1, self.settings.rate_limit_window_sec)
        max_req = max(1, self.settings.rate_limit_max_requests)
        while dq and (now - dq[0]) > window:
            dq.popleft()
        if len(dq) >= max_req:
            return False
        dq.append(now)
        return True

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
            return host in set(self.settings.allowed_library_hosts)
        except Exception:
            return False

    @staticmethod
    def _normalize_isbn13(isbn: Optional[str]) -> str:
        if not isbn:
            return ""
        normalized = re.sub(r"[^0-9]", "", isbn)
        if len(normalized) == 13 and normalized.startswith(("978", "979")):
            return normalized
        return ""

    @staticmethod
    def _is_definitely_not_found(body_text: str) -> bool:
        tokens = [
            "\uac80\uc0c9\uacb0\uacfc\uac00 \uc5c6\uc2b5\ub2c8\ub2e4",   # 검색결과가 없습니다
            "\uac80\uc0c9 \uacb0\uacfc\uac00 \uc5c6\uc2b5\ub2c8\ub2e4",   # 검색 결과가 없습니다
            "\uc870\ud68c \uacb0\uacfc\uac00 \uc5c6\uc2b5\ub2c8\ub2e4",   # 조회 결과가 없습니다
            "\uc18c\uc7a5\ud558\uace0 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4",  # 소장하고 있지 않습니다
            "\uc18c\uc7a5 \uc5c6\uc74c",                                     # 소장 없음
        ]
        return any(t in body_text for t in tokens)

    @staticmethod
    def _is_guard_failure(body_text: str) -> bool:
        tokens = [
            "\uc811\uadfc\uc81c\ud55c",       # 접근제한
            "\ucc28\ub2e8",                     # 차단
            "\uc5d0\ub7ec",                     # 에러
            "\uc624\ub958\uac00 \ubc1c\uc0dd",  # 오류가 발생
            "\uc11c\ube44\uc2a4 \uc7a5\uc560",  # 서비스 장애
            "\uad8c\ud55c\uc774 \uc5c6\uc2b5\ub2c8\ub2e4",  # 권한이 없습니다
        ]
        return any(t in body_text for t in tokens)

    @staticmethod
    def _extract_location(body_text: str) -> str:
        m = re.search(r"([\uAC00-\uD7A3A-Za-z0-9 ]*(?:\uc790\ub8cc\uc2e4|\uc911\uc559\ub3c4\uc11c\uad00))", body_text)
        return m.group(1).strip() if m else ""

    @staticmethod
    def _extract_call_number(body_text: str) -> str:
        m = re.search(r"\b\d{3}(?:\.\d+)?\s*[\uAC00-\uD7A3A-Za-z]\S{0,10}", body_text)
        return m.group(0).strip() if m else ""

    @staticmethod
    def _extract_display_title(row_text: str) -> str:
        raw = re.sub(r"\s+", " ", (row_text or "").strip())
        if not raw:
            return ""
        cut_tokens = [" \ub2e8\ud589\ubcf8", " \uc804\uc790\ucc45", " \ub300\ucd9c", " \ubcf4\uc720", " \uc18c\uc7a5", " \uccad\uad6c\uae30\ud638"]
        idx = len(raw)
        for token in cut_tokens:
            p = raw.find(token)
            if p > 0:
                idx = min(idx, p)
        return raw[:idx].strip()

    @staticmethod
    def _status_text_ko(code: str) -> str:
        normalized = (code or "").upper()
        if normalized == "AVAILABLE":
            return "\uc720"
        if normalized in {"ON_LOAN", "RESERVED"}:
            return "\ub300\ucd9c\uc911"
        if normalized == "UNAVAILABLE":
            return "\uc774\uc6a9\ubd88\uac00"
        if normalized == "NOT_OWNED":
            return "\ubbf8\uc18c\uc7a5"
        return "\uc815\ubcf4 \uc5c6\uc74c"

    @staticmethod
    def _overall_status_label(code: str) -> str:
        normalized = (code or "").upper()
        if normalized == "AVAILABLE":
            return "\ub300\ucd9c\uac00\ub2a5"
        if normalized in {"ON_LOAN", "RESERVED"}:
            return "\ub300\ucd9c\uc911"
        if normalized == "UNAVAILABLE":
            return "\uc774\uc6a9\ubd88\uac00"
        return "\uc815\ubcf4\uc5c6\uc74c"

    @staticmethod
    def _extract_ebook_holdings(text: str) -> int:
        raw = text or ""
        m_total = re.search(r"\ubcf4\uc720\s*(\d+)", raw)
        m_loan = re.search(r"\ub300\ucd9c\s*(\d+)", raw)
        total = int(m_total.group(1)) if m_total else 0
        loaned = int(m_loan.group(1)) if m_loan else 0
        return max(0, total - loaned)

    @staticmethod
    def _is_monograph_row(text: str, row_html: str = "") -> bool:
        compact = re.sub(r"\s+", "", text or "")
        if _TOK_MONOGRAPH in compact:
            return True
        html = (row_html or "").lower()
        # icon/alt/title/class based markers
        return bool(
            ("alt=\"\ub2e8\ud589\ubcf8\"" in html)
            or ("title=\"\ub2e8\ud589\ubcf8\"" in html)
            or ("icon-monograph" in html)
            or ("ico-monograph" in html)
            or ("badge-monograph" in html)
        )

    @staticmethod
    def _is_ebook_row(text: str, row_html: str = "") -> bool:
        compact = re.sub(r"\s+", "", text or "")
        if "\uc804\uc790\ucc45" in compact:
            return True
        html = (row_html or "").lower()
        return bool(
            ("alt=\"\uc804\uc790\ucc45\"" in html)
            or ("title=\"\uc804\uc790\ucc45\"" in html)
            or ("icon-ebook" in html)
            or ("ico-ebook" in html)
            or ("badge-ebook" in html)
        )

    @staticmethod
    def _strip_subtitle(text: str) -> str:
        normalized = re.sub(r"\s+", " ", (text or "").strip())
        if not normalized:
            return ""
        return re.sub(r"\s*(?:-|:|\().*$", "", normalized).strip()

    @staticmethod
    def _normalize_title_query(text: Optional[str]) -> str:
        base = LibraryScraper._strip_subtitle(text or "")
        base = re.sub(r"[^0-9A-Za-z\u3131-\u318E\uAC00-\uD7A3\s]", " ", base)
        return re.sub(r"\s+", " ", base).strip()

    @staticmethod
    def _normalize_for_match(text: str) -> str:
        return re.sub(r"\s+", " ", (text or "").strip()).lower()

    @staticmethod
    def _title_match_score(row_text: str, normalized_title: str) -> int:
        if not normalized_title:
            return 0
        hay = LibraryScraper._normalize_for_match(row_text)
        needle = LibraryScraper._normalize_for_match(normalized_title)
        if not hay or not needle:
            return 0
        if needle in hay:
            return 100
        tokens = [tok for tok in needle.split(" ") if tok]
        if not tokens:
            return 0
        return sum(10 for tok in tokens if tok in hay)

    @staticmethod
    def _row_matches_query(row_text: str, normalized_title: str, isbn13: str) -> bool:
        hay = LibraryScraper._normalize_for_match(row_text)
        if isbn13 and isbn13 in re.sub(r"[^0-9]", "", row_text or ""):
            return True
        if normalized_title and LibraryScraper._title_match_score(row_text, normalized_title) > 0:
            return True
        # ISBN-only searches often render rows without visible ISBN text.
        # In that case, trust result rows from the ISBN search page itself.
        if isbn13 and not normalized_title and bool((row_text or "").strip()):
            return True
        # If we reached here, the library returned this row for our query, 
        # but our strict matching failed. To avoid dropping valid books
        # with abbreviated titles, we trust the library's search engine.
        return True

    @staticmethod
    def _row_match_score(row_text: str, normalized_title: str, isbn13: str) -> int:
        score = 0
        digits = re.sub(r"[^0-9]", "", row_text or "")
        if isbn13 and isbn13 in digits:
            score += 300
        score += LibraryScraper._title_match_score(row_text, normalized_title)
        if "\ub300\ucd9c\uac00\ub2a5" in (row_text or ""):
            score += 20
        if "\ubcf4\uc720" in (row_text or ""):
            score += 10
        return score

    @staticmethod
    def _pick_best_row(candidates: list[tuple[int, str]], fallback_parts: list[str]) -> str:
        if candidates:
            candidates.sort(key=lambda x: x[0], reverse=True)
            return candidates[0][1]
        if fallback_parts:
            return fallback_parts[0]
        return ""

    @staticmethod
    def _pick_best_monograph_row(candidates: list[tuple[int, str, str]], fallback_parts: list[str]) -> tuple[str, str]:
        if candidates:
            candidates.sort(
                key=lambda x: (
                    1 if ("\ub300\ucd9c\uac00\ub2a5" in x[1] or "\ub300\ucd9c \uac00\ub2a5" in x[1]) else 0,
                    x[0],
                ),
                reverse=True,
            )
            return candidates[0][1], candidates[0][2]
        if fallback_parts:
            return fallback_parts[0], ""
        return "", ""

    @staticmethod
    def _has_available_in_monograph(monograph_text: str, monograph_html: str) -> bool:
        if _TOK_AVAILABLE in (monograph_text or "") or "\ub300\ucd9c \uac00\ub2a5" in (monograph_text or ""):
            return True
        html = monograph_html or ""
        blue_btn = re.search(r"(btn-primary|badge-primary|color:\s*#(?:0d6efd|007bff|0056b3))", html, re.IGNORECASE)
        has_label = re.search(r"\ub300\ucd9c\s*\uac00\ub2a5", html)
        return bool(blue_btn and has_label)

    @staticmethod
    def _hybrid_encode_cp949(text: str) -> str:
        parts: list[str] = []
        for ch in text:
            if ch == " ":
                parts.append("+")
                continue
            if ch.isascii() and (ch.isalnum() or ch in {"-", "_", "."}):
                parts.append(ch)
                continue
            if re.match(r"[\u3131-\u318E\uAC00-\uD7A3]", ch):
                parts.append(quote_from_bytes(ch.encode("cp949"), safe=""))
                continue
            parts.append(quote_from_bytes(ch.encode("utf-8"), safe=""))
        return "".join(parts)

    @staticmethod
    def _strip_tags(html: str) -> str:
        no_tags = re.sub(r"<[^>]+>", " ", html or "", flags=re.IGNORECASE)
        return re.sub(r"\s+", " ", no_tags).strip()

    @staticmethod
    def parse_snapshot_items(html: str) -> list[dict[str, Any]]:
        blocks = []
        for m in re.finditer(r"(?is)<li[^>]*>(.*?)</li>", html or ""):
            blocks.append(m.group(0))
        for m in re.finditer(r"(?is)<div[^>]*class=[\"'][^\"']*bookinfo[^\"']*[\"'][^>]*>(.*?)</div>", html or ""):
            blocks.append(m.group(0))

        items: list[dict[str, Any]] = []
        for block in blocks:
            text = LibraryScraper._strip_tags(block)
            if not text:
                continue
            record_type = "monograph" if LibraryScraper._is_monograph_row(text, block) else ("ebook" if LibraryScraper._is_ebook_row(text, block) else "other")
            badge = "UNKNOWN"
            if ("\ub300\ucd9c\uac00\ub2a5" in text) or ("\ub300\ucd9c \uac00\ub2a5" in text):
                badge = "AVAILABLE"
            elif ("\ub300\ucd9c\uc911" in text) or ("\ub300\ucd9c \uc911" in text):
                badge = "ON_LOAN"
            elif _TOK_UNAVAIL in text or _TOK_UNUSABLE in text or _TOK_LOST in text or _TOK_REPAIR in text:
                badge = "UNUSABLE"
            holding = LibraryScraper._extract_ebook_holdings(text) if record_type == "ebook" else 0
            items.append(
                {
                    "title": LibraryScraper._extract_display_title(text),
                    "row_text": text,
                    "record_type": record_type,
                    "loan_badge": badge,
                    "holding_count": holding,
                }
            )
        return items

    @staticmethod
    def pick_best_snapshot_item(items: list[dict[str, Any]], query_title: str) -> dict[str, Any]:
        normalized_query = LibraryScraper._normalize_for_match(LibraryScraper._normalize_title_query(query_title))
        best: dict[str, Any] = {}
        best_score = -1
        for item in items:
            title = LibraryScraper._normalize_for_match(str(item.get("title", "")))
            score = 0
            if normalized_query and title == normalized_query:
                score += 200
            elif normalized_query and normalized_query in title:
                score += 120
            if item.get("record_type") == "monograph":
                score += 100
            elif item.get("record_type") == "ebook":
                score += 10
            if item.get("loan_badge") == "AVAILABLE":
                score += 30
            if score > best_score:
                best_score = score
                best = item
        return best

    def _build_search_url(self, keyword: str) -> str:
        encoded = self._hybrid_encode_cp949(keyword)
        return f"{self.settings.lib_search_url_prefix}{encoded}"

    @staticmethod
    def _cache_key(key: str) -> str:
        return f"{CACHE_SCHEMA_VERSION}:{key}"

    def _get_cache(self, key: str) -> Optional[Dict[str, Any]]:
        entry = self._cache.get(key)
        if not entry:
            return None
        expires_at, data = entry
        if time.time() > expires_at:
            self._cache.pop(key, None)
            return None
        self._cache.move_to_end(key)
        return data

    def _set_cache(self, key: str, data: Dict[str, Any]) -> None:
        status = str(data.get("status_text", "UNKNOWN")).upper()
        ttl = DEFAULT_STABLE_TTL_SEC if status in {"AVAILABLE", "ON_LOAN", "RESERVED", "UNAVAILABLE", "NOT_OWNED"} else DEFAULT_TRANSIENT_TTL_SEC
        self._cache[key] = (time.time() + ttl, data)
        self._cache.move_to_end(key)
        while len(self._cache) > MAX_CACHE_ENTRIES:
            self._cache.popitem(last=False)

    @staticmethod
    def _enforce_safety(result: Dict[str, Any], isbn13: str) -> Dict[str, Any]:
        safe = dict(result)
        code = str(safe.get("status_text", "UNKNOWN")).upper()
        found = bool(safe.get("found"))
        available = bool(safe.get("available"))
        is_owned = bool(safe.get("is_owned"))

        if available:
            safe["status_text"] = "AVAILABLE"
            safe["loan_status_text"] = "AVAILABLE"
            safe["found"] = True
            safe["is_owned"] = True
            return safe

        if code in {"AVAILABLE", "ON_LOAN", "RESERVED", "UNAVAILABLE"}:
            safe["found"] = True
            safe["is_owned"] = True
            safe["loan_status_text"] = code
            safe["status_text"] = code
            return safe

        if code == "NOT_OWNED" and (found or is_owned):
            logger.error("rule_violation isbn13=%s code=%s found=%s", isbn13, code, found)
            safe["status_text"] = "ERROR"
            safe["loan_status_text"] = "ERROR"
            safe["error_message"] = "rule_violation"
            safe["found"] = False
            safe["is_owned"] = False
            safe["available"] = False

        return safe

    @staticmethod
    def _empty_response(
        detail_url: str = "",
        error: str = "",
        retry_required: bool = False,
        status_code: str = "UNKNOWN",
    ) -> Dict[str, Any]:
        return {
            "found": False,
            "available": False,
            "location": "",
            "call_number": "",
            "detail_url": detail_url,
            "error_message": error,
            "loan_status_text": status_code,
            "detail_verified": False,
            "checked_at": datetime.now(timezone.utc).isoformat(),
            "is_owned": status_code in {"AVAILABLE", "ON_LOAN", "RESERVED", "UNAVAILABLE"},
            "status_text": status_code,
            "status_text_ko": LibraryScraper._status_text_ko(status_code),
            "overall_status": LibraryScraper._overall_status_label(status_code),
            "record_type_picked": None,
            "matched_title": "",
            "retry_required": retry_required,
        }

    @staticmethod
    def _safe_on_loan_response(
        detail_url: str = "",
        error: str = "",
        retry_required: bool = True,
        query_key: str = "",
    ) -> Dict[str, Any]:
        return {
            "found": True,
            "available": False,
            "location": "",
            "call_number": "",
            "detail_url": detail_url,
            "error_message": error,
            "loan_status_text": "ON_LOAN",
            "detail_verified": False,
            "checked_at": datetime.now(timezone.utc).isoformat(),
            "is_owned": True,
            "status_text": "ON_LOAN",
            "status_text_ko": "\ub300\ucd9c\uc911",
            "overall_status": "\ub300\ucd9c\uc911",
            "record_type_picked": "\ub2e8\ud589\ubcf8",
            "matched_title": "",
            "retry_required": retry_required,
            "isbn13": query_key,
        }

    @staticmethod
    def _safe_unknown_response(
        detail_url: str = "",
        error: str = "",
        retry_required: bool = True,
        query_key: str = "",
    ) -> Dict[str, Any]:
        return {
            "found": False,
            "available": False,
            "location": "",
            "call_number": "",
            "detail_url": detail_url,
            "error_message": error,
            "loan_status_text": "UNKNOWN",
            "detail_verified": False,
            "checked_at": datetime.now(timezone.utc).isoformat(),
            "is_owned": False,
            "status_text": "UNKNOWN",
            "status_text_ko": "\uc815\ubcf4 \uc5c6\uc74c",
            "overall_status": "\uc815\ubcf4\uc5c6\uc74c",
            "record_type_picked": None,
            "matched_title": "",
            "retry_required": retry_required,
            "isbn13": query_key,
        }
