from __future__ import annotations

import asyncio
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Optional
from urllib.parse import quote

from dotenv import load_dotenv
from playwright.async_api import Browser, BrowserContext, Error, Page, Playwright, async_playwright


TIMEOUT_MS = 10000
ROW_SELECTORS = ["li.mb-4", ".bookinfo", "div.bookinfo", "table tbody tr", "ul > li"]
TITLE_SELECTORS = [".title", ".tit", "h3", "h4", "a[title]", "a", "strong"]

TXT_MONOGRAPH = "\uB2E8\uD589\uBCF8"
TXT_EBOOK = "\uC804\uC790\uCC45"
TXT_HOLDING = "\uBCF4\uC720"
TXT_AVAILABLE = "\uB300\uCD9C\uAC00\uB2A5"
TXT_ON_LOAN = "\uB300\uCD9C\uC911"
TXT_OWNED = "\uC18C\uC7A5"
TXT_NOT_OWNED = "\uBBF8\uC18C\uC7A5"
TXT_FALLBACK = "\uC815\uBCF4 \uC5C6\uC74C/\uBBF8\uC18C\uC7A5"


def load_library_env() -> None:
    base_dir = Path(__file__).resolve().parent
    for env_path, override in [(base_dir.parent / ".env", False), (base_dir / ".env", True)]:
        if not env_path.exists():
            continue
        for enc in ("utf-8-sig", "cp949"):
            try:
                load_dotenv(env_path, encoding=enc, override=override)
                break
            except UnicodeDecodeError:
                continue


def clean_title(raw: str) -> str:
    text = re.sub(r"\s+", " ", (raw or "").strip())
    if not text:
        return ""
    text = re.sub(r"\s*(?:-|:|\().*$", "", text).strip()
    text = re.sub(r"[^0-9A-Za-z\u3131-\u318E\uAC00-\uD7A3\s]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def normalize_text(raw: str) -> str:
    return re.sub(r"\s+", " ", (raw or "").strip()).lower()


def is_korean_char(ch: str) -> bool:
    return bool(re.match(r"[\u3131-\u318E\uAC00-\uD7A3]", ch))


def hybrid_encode_query(query: str) -> str:
    out: list[str] = []
    for ch in query:
        if is_korean_char(ch):
            out.append("".join(f"%{b:02X}" for b in ch.encode("cp949", errors="replace")))
            continue
        if ch == " ":
            out.append("%20")
            continue
        if ch.isascii() and (ch.isalnum() or ch in "-_.~"):
            out.append(ch)
            continue
        out.append(quote(ch, safe=""))
    return "".join(out)


def is_monograph_text(row_text: str) -> bool:
    compact = re.sub(r"\s+", "", row_text or "")
    return TXT_MONOGRAPH in compact


def is_ebook_text(row_text: str) -> bool:
    compact = re.sub(r"\s+", "", row_text or "")
    return TXT_EBOOK in compact


def parse_ebook_holding_count(row_text: str) -> int:
    patterns = [
        rf"{TXT_HOLDING}\s*[:：]?\s*(\d+)",
        rf"{TXT_HOLDING}\s*\(\s*(\d+)\s*\)",
        rf"{TXT_HOLDING}\s*(\d+)",
    ]
    for p in patterns:
        m = re.search(p, row_text or "")
        if m:
            return int(m.group(1))
    return 0


# Override parser to handle mojibake-safe and variant formats deterministically.
def parse_ebook_holding_count(row_text: str) -> int:
    patterns = [
        r"\ubcf4\uc720\s*[:：]?\s*(\d+)",
        r"\ubcf4\uc720\s*\(\s*(\d+)\s*\)",
        r"\ubcf4\uc720\s*(\d+)",
    ]
    raw = row_text or ""
    for p in patterns:
        m = re.search(p, raw)
        if m:
            return int(m.group(1))
    return 0


@dataclass
class SearchResult:
    query_title_raw: str
    query_title_clean: str
    matched_title: str
    material_type: str
    status: str
    holding_count: int
    reason: str
    error_type: str

    def to_dict(self) -> dict:
        return {
            "query_title_raw": self.query_title_raw,
            "query_title_clean": self.query_title_clean,
            "matched_title": self.matched_title,
            "material_type": self.material_type,
            "status": self.status,
            "holding_count": self.holding_count,
            "reason": self.reason,
            "error_type": self.error_type,
        }


class LibrarySessionCrawler:
    def __init__(self) -> None:
        load_library_env()
        self.library_id = os.getenv("LIBRARY_ID", "").strip()
        self.library_pw = os.getenv("LIBRARY_PW", "").strip()
        self.login_url = os.getenv("LIB_LOGIN_URL", "https://lib.yju.ac.kr/Cheetah/Login/Login").strip()
        self.search_prefix = os.getenv(
            "LIB_SEARCH_URL_PREFIX",
            "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/",
        ).strip()
        self.storage_state_path = os.getenv("LIB_STORAGE_STATE_PATH", "library_state.json").strip()

        self._pw: Optional[Playwright] = None
        self._browser: Optional[Browser] = None
        self._context: Optional[BrowserContext] = None
        self._lock = asyncio.Lock()
        self._login_lock = asyncio.Lock()
        self._logged_in = False

    async def start(self) -> None:
        async with self._lock:
            if self._context is not None:
                return
            self._pw = await async_playwright().start()
            self._browser = await self._pw.chromium.launch(headless=True, args=["--no-sandbox", "--disable-setuid-sandbox"])
            storage_state = self.storage_state_path if Path(self.storage_state_path).exists() else None
            self._context = await self._browser.new_context(storage_state=storage_state)
            self._logged_in = False

    async def close(self) -> None:
        async with self._lock:
            if self._context is not None:
                await self._context.close()
                self._context = None
            if self._browser is not None:
                await self._browser.close()
                self._browser = None
            if self._pw is not None:
                await self._pw.stop()
                self._pw = None
            self._logged_in = False

    async def _new_page(self) -> Page:
        if self._context is None:
            await self.start()
        if self._context is None:
            raise RuntimeError("browser_context_unavailable")
        page = await self._context.new_page()
        page.set_default_timeout(TIMEOUT_MS)
        page.set_default_navigation_timeout(TIMEOUT_MS)
        return page

    async def _find_first(self, page: Page, selectors_csv: str):
        selectors = [s.strip() for s in selectors_csv.split(",") if s.strip()]
        for sel in selectors:
            try:
                await page.wait_for_selector(sel, timeout=TIMEOUT_MS, state="attached")
                loc = page.locator(sel).first
                if await loc.count() > 0:
                    return loc
            except Error:
                continue
        return None

    async def ensure_login(self) -> tuple[bool, str]:
        if self._logged_in:
            return True, "already_logged_in"
        if not self.library_id or not self.library_pw:
            return False, "missing_library_credentials"

        async with self._login_lock:
            if self._logged_in:
                return True, "already_logged_in"

            page = await self._new_page()
            try:
                await page.goto(self.login_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
                id_input = await self._find_first(
                    page,
                    "#formText input[name='loginId'], #formText input[id='loginId'], input[name='id'], input[name='userId']",
                )
                pw_input = await self._find_first(
                    page,
                    "#formText input[name='loginpwd'], #formText input[id='loginpwd'], input[name='password'], input[type='password']",
                )
                submit_btn = await self._find_first(
                    page,
                    "#formText button[type='submit'], button[type='submit'], input[type='submit']",
                )
                if id_input is None or pw_input is None or submit_btn is None:
                    return False, "login_form_not_found"

                await id_input.fill(self.library_id, timeout=TIMEOUT_MS)
                await pw_input.fill(self.library_pw, timeout=TIMEOUT_MS)
                await submit_btn.click(timeout=TIMEOUT_MS)
                try:
                    await page.wait_for_selector(
                        "a[href*='Logout'], a[href*='logout'], a[href*='MyPage'], .myPage, .user, .log-out",
                        timeout=TIMEOUT_MS,
                        state="attached",
                    )
                except Error:
                    return False, "login_verify_failed"

                if self._context is not None:
                    await self._context.storage_state(path=self.storage_state_path)
                self._logged_in = True
                return True, "login_success"
            except Error:
                return False, "login_timeout_or_error"
            finally:
                await page.close()

    async def _extract_row_title(self, row) -> str:
        for sel in TITLE_SELECTORS:
            try:
                loc = row.locator(sel).first
                if await loc.count() <= 0:
                    continue
                text = re.sub(r"\s+", " ", (await loc.inner_text(timeout=TIMEOUT_MS)).strip())
                if text:
                    return text
            except Error:
                continue
        try:
            full = re.sub(r"\s+", " ", (await row.inner_text(timeout=TIMEOUT_MS)).strip())
            if not full:
                return ""
            return full.split("  ")[0].strip()
        except Error:
            return ""

    async def _collect_candidate_rows(self, page: Page) -> list:
        rows = []
        # Use short timeout per selector (1s) — SPA has already rendered after networkidle.
        # Sequential 10s waits (5 selectors = 50s) would exceed the 12s search timeout.
        for sel in ROW_SELECTORS:
            try:
                await page.wait_for_selector(sel, timeout=1000, state="attached")
            except Error:
                continue
            loc = page.locator(sel)
            count = await loc.count()
            if count <= 0:
                continue
            for i in range(min(count, 60)):
                rows.append(loc.nth(i))
            if rows:
                break
        return rows

    async def search(self, title: str) -> dict:
        raw = title or ""
        query_clean = clean_title(raw)
        if not query_clean:
            return SearchResult(
                query_title_raw=raw,
                query_title_clean="",
                matched_title="",
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason="empty_title",
                error_type="validation",
            ).to_dict()

        ok, reason = await self.ensure_login()
        if not ok:
            return SearchResult(
                query_title_raw=raw,
                query_title_clean=query_clean,
                matched_title="",
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason=reason,
                error_type="login",
            ).to_dict()

        encoded = hybrid_encode_query(query_clean)
        search_url = f"{self.search_prefix}{encoded}"

        page = await self._new_page()
        try:
            await page.goto(search_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
            # Wait for SPA AJAX calls to complete before reading rows
            try:
                await page.wait_for_load_state("networkidle", timeout=TIMEOUT_MS)
            except Error:
                pass
            candidates = await self._collect_candidate_rows(page)
            if not candidates:
                return SearchResult(
                    query_title_raw=raw,
                    query_title_clean=query_clean,
                    matched_title="",
                    material_type="unknown",
                    status=TXT_FALLBACK,
                    holding_count=0,
                    reason="no_rows",
                    error_type="not_found",
                ).to_dict()

            exact_rows = []
            norm_query = normalize_text(query_clean)
            for row in candidates:
                row_title = await self._extract_row_title(row)
                row_clean = clean_title(row_title)
                if normalize_text(row_clean) != norm_query:
                    continue
                row_text = re.sub(r"\s+", " ", (await row.inner_text(timeout=TIMEOUT_MS)).strip())
                exact_rows.append((row, row_title, row_text))

            if not exact_rows:
                return SearchResult(
                    query_title_raw=raw,
                    query_title_clean=query_clean,
                    matched_title="",
                    material_type="unknown",
                    status=TXT_FALLBACK,
                    holding_count=0,
                    reason="no_exact_title_row",
                    error_type="not_found",
                ).to_dict()

            monograph_rows = [r for r in exact_rows if is_monograph_text(r[2])]
            if monograph_rows:
                row, matched_title, row_text = monograph_rows[0]
                status = TXT_ON_LOAN
                try:
                    has_available = await row.locator(r"text=/\uB300\uCD9C\s*\uAC00\uB2A5/").count() > 0
                    if not has_available and re.search(r"\uB300\uCD9C\s*\uAC00\uB2A5", row_text):
                        has_available = True
                    status = TXT_OWNED if has_available else TXT_ON_LOAN
                except Error:
                    status = TXT_ON_LOAN
                return SearchResult(
                    query_title_raw=raw,
                    query_title_clean=query_clean,
                    matched_title=matched_title,
                    material_type=TXT_MONOGRAPH,
                    status=status,
                    holding_count=0,
                    reason="monograph_row_scoped",
                    error_type="",
                ).to_dict()

            ebook_rows = [r for r in exact_rows if is_ebook_text(r[2])]
            if ebook_rows:
                _, matched_title, row_text = ebook_rows[0]
                holding = parse_ebook_holding_count(row_text)
                return SearchResult(
                    query_title_raw=raw,
                    query_title_clean=query_clean,
                    matched_title=matched_title,
                    material_type=TXT_EBOOK,
                    status=TXT_OWNED if holding >= 1 else TXT_NOT_OWNED,
                    holding_count=holding,
                    reason="ebook_row_scoped",
                    error_type="",
                ).to_dict()

            return SearchResult(
                query_title_raw=raw,
                query_title_clean=query_clean,
                matched_title="",
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason="type_row_not_found",
                error_type="not_found",
            ).to_dict()
        except Error:
            return SearchResult(
                query_title_raw=raw,
                query_title_clean=query_clean,
                matched_title="",
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason="timeout_or_playwright_error",
                error_type="timeout",
            ).to_dict()
        finally:
            await page.close()
