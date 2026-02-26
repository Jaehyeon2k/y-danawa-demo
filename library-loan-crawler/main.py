"""
YJU library loan crawler using Playwright.
- Loads credentials from library-loan-crawler/.env
- Logs in and scrapes current loan rows
"""

import asyncio
import json
import logging
import os
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import List

from playwright.async_api import TimeoutError as PlaywrightTimeoutError
from playwright.async_api import async_playwright

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("loan-crawler")


@dataclass
class LoanItem:
    title: str
    due_date: str
    status: str


STATUS_KEYWORDS = [
    "대출가능",
    "대출 가능",
    "대출중",
    "대출 중",
    "대출불가",
    "대출 불가",
    "반납예정",
    "반납 예정",
    "예약중",
    "예약 중",
    "예약가능",
    "예약 가능",
    "연체",
    "loan",
    "overdue",
    "returned",
    "renew",
    "available",
    "on loan",
    "unavailable",
]


def _load_env() -> None:
    env_path = Path(__file__).resolve().parent / ".env"
    if not env_path.exists():
        log.warning(".env not found: %s", env_path)
        log.warning("Create it from .env.example")
        return

    if load_dotenv is not None:
        # Windows editors often write BOM in UTF-8 files.
        load_dotenv(env_path, encoding="utf-8-sig")
        log.info("Loaded .env: %s", env_path)
        return

    # Fallback loader for environments where python-dotenv is unavailable.
    for line in env_path.read_text(encoding="utf-8-sig").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'").strip('"')
        if key and key not in os.environ:
            os.environ[key] = value
    log.info("Loaded .env with internal parser: %s", env_path)


def _require_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise ValueError(
            f"Missing environment variable: {name}\n"
            f"Add {name}=... to library-loan-crawler/.env"
        )
    return value


def _is_date_like(text: str) -> bool:
    return bool(re.search(r"\d{4}[-/.]\d{1,2}[-/.]\d{1,2}", text))


def _looks_like_status(text: str) -> bool:
    s = text.strip().lower()
    return any(k in s for k in STATUS_KEYWORDS)


def _normalize(value: str) -> str:
    return re.sub(r"\s+", " ", (value or "").strip())


def _split_selectors(raw: str) -> List[str]:
    return [s.strip() for s in raw.split(",") if s.strip()]


def _parse_int_env(name: str) -> int:
    raw = os.getenv(name, "").strip()
    if not raw:
        return -1
    try:
        return int(raw)
    except ValueError:
        return -1


def _looks_like_id_code(text: str) -> bool:
    # e.g. EM292262, C123456 like internal accession/copy codes.
    return bool(re.fullmatch(r"[A-Za-z]{1,4}\d{4,}", text.strip()))


def _pick_status(cells: List[str], status_idx: int) -> str:
    if 0 <= status_idx < len(cells) and _looks_like_status(cells[status_idx]):
        return cells[status_idx]
    return next((c for c in cells if _looks_like_status(c)), "")


def _pick_due_date(cells: List[str], due_idx: int) -> str:
    if 0 <= due_idx < len(cells) and _is_date_like(cells[due_idx]):
        return cells[due_idx]
    return next((c for c in cells if _is_date_like(c)), "")


def _pick_title(cells: List[str], title_idx: int) -> str:
    if 0 <= title_idx < len(cells):
        candidate = cells[title_idx]
        if candidate and not _looks_like_status(candidate) and not _is_date_like(candidate):
            return candidate

    # Prefer meaningful text over accession/copy codes.
    for c in cells:
        if _is_date_like(c) or _looks_like_status(c):
            continue
        if _looks_like_id_code(c):
            continue
        return c
    return next((c for c in cells if not _is_date_like(c) and not _looks_like_status(c)), "")


async def _resolve_header_indices(page, table_sel: str) -> dict:
    header_map = {"title_idx": -1, "due_idx": -1, "status_idx": -1}
    header_tokens = {
        "title_idx": ["서명", "자료명", "제목", "도서명", "title"],
        "due_idx": ["반납예정일", "반납예정", "반납일", "예정일", "due"],
        "status_idx": ["대출상태", "상태", "status", "현황"],
    }

    selectors = _split_selectors(table_sel) if table_sel else ["table"]
    selectors.append("table")
    for sel in selectors:
        header_cells = page.locator(f"{sel} thead tr th")
        count = await header_cells.count()
        if count <= 0:
            continue

        headers: List[str] = []
        for i in range(count):
            txt = _normalize(await header_cells.nth(i).inner_text()).lower()
            headers.append(txt)

        for key, tokens in header_tokens.items():
            for i, h in enumerate(headers):
                if any(t in h for t in tokens):
                    header_map[key] = i
                    break
        if header_map["due_idx"] >= 0 or header_map["status_idx"] >= 0 or header_map["title_idx"] >= 0:
            break

    return header_map


async def _first_visible(page, selectors_csv: str):
    for sel in _split_selectors(selectors_csv):
        loc = page.locator(sel).first
        if await loc.count() > 0:
            return loc
    return None


async def _open_login_form_if_needed(page) -> None:
    await page.wait_for_load_state("domcontentloaded")
    try:
        await page.wait_for_load_state("networkidle", timeout=5000)
    except Exception:
        pass

    if await page.locator("#formText input[name='loginId']").count() > 0:
        return

    for sel in ("a.log-in", "a.log-in-mobile", "a[href*='/Cheetah/Login/Login']"):
        loc = page.locator(sel).first
        if await loc.count() == 0:
            continue
        await loc.click()
        await page.wait_for_load_state("domcontentloaded")
        if await page.locator("#formText input[name='loginId']").count() > 0:
            return

    # Fallback: open login endpoint directly.
    await page.goto("https://lib.yju.ac.kr/Cheetah/Login/Login", wait_until="domcontentloaded")


async def login_and_scrape() -> List[LoanItem]:
    _load_env()

    login_url = _require_env("LIB_LOGIN_URL")
    loan_url = _require_env("LIB_LOAN_URL")
    student_id = _require_env("LIB_USER_ID")
    student_pw = _require_env("LIB_USER_PASSWORD")

    headless = os.getenv("PLAYWRIGHT_HEADLESS", "true").lower() not in {"0", "false", "no"}
    timeout_ms = int(os.getenv("PLAYWRIGHT_TIMEOUT_MS", "30000"))

    id_sel = os.getenv(
        "LIB_ID_SELECTOR",
        "#formText input[name='loginId'], #formText input[id='loginId'], input[name='id'], input[name='userId']",
    )
    pw_sel = os.getenv(
        "LIB_PASSWORD_SELECTOR",
        "#formText input[name='loginpwd'], #formText input[id='loginpwd'], input[name='password'], input[id*='password'], input[type='password']",
    )
    submit_sel = os.getenv(
        "LIB_SUBMIT_SELECTOR",
        "#formText button[type='submit'], button[type='submit']:has-text('로그인'), input[type='submit']",
    )
    row_sel = os.getenv("LIB_LOAN_ROW_SELECTOR", "table tbody tr")
    table_sel = os.getenv("LIB_LOAN_TABLE_SELECTOR", "table")
    title_idx_override = _parse_int_env("LIB_LOAN_TITLE_IDX")
    due_idx_override = _parse_int_env("LIB_LOAN_DUE_IDX")
    status_idx_override = _parse_int_env("LIB_LOAN_STATUS_IDX")

    log.info("Target login page: %s", login_url)
    log.info("headless=%s timeout=%sms", headless, timeout_ms)

    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=headless)
            context = await browser.new_context()
            page = await context.new_page()
            page.set_default_timeout(timeout_ms)

            try:
                log.info("[1/3] Opening login page")
                await page.goto(login_url, wait_until="domcontentloaded")
                await _open_login_form_if_needed(page)

                id_input = await _first_visible(page, id_sel)
                pw_input = await _first_visible(page, pw_sel)
                submit_btn = await _first_visible(page, submit_sel)

                if not id_input or not pw_input or not submit_btn:
                    raise RuntimeError(
                        "Login form elements not found. Check selectors in .env: "
                        "LIB_ID_SELECTOR / LIB_PASSWORD_SELECTOR / LIB_SUBMIT_SELECTOR"
                    )

                await id_input.fill(student_id)
                await pw_input.fill(student_pw)
                await submit_btn.click()
                await page.wait_for_load_state("networkidle")
                log.info("[1/3] Login submitted")

                log.info("[2/3] Opening loan page")
                await page.goto(loan_url, wait_until="domcontentloaded")
                await page.wait_for_load_state("networkidle")

                # Some installations may redirect to login with returnUrl if auth is required.
                if "Login/Login" in page.url:
                    await _open_login_form_if_needed(page)
                    id_input = await _first_visible(page, id_sel)
                    pw_input = await _first_visible(page, pw_sel)
                    submit_btn = await _first_visible(page, submit_sel)
                    if id_input and pw_input and submit_btn:
                        await id_input.fill(student_id)
                        await pw_input.fill(student_pw)
                        await submit_btn.click()
                        await page.wait_for_load_state("networkidle")
                        await page.goto(loan_url, wait_until="domcontentloaded")
                        await page.wait_for_load_state("networkidle")

                log.info("[3/3] Scraping loan rows")
                rows = page.locator(row_sel)
                row_count = await rows.count()
                header_idx = await _resolve_header_indices(page, table_sel)
                title_idx = title_idx_override if title_idx_override >= 0 else header_idx["title_idx"]
                due_idx = due_idx_override if due_idx_override >= 0 else header_idx["due_idx"]
                status_idx = status_idx_override if status_idx_override >= 0 else header_idx["status_idx"]

                if row_count == 0:
                    log.warning("No loan rows found with selector: %s", row_sel)
                    return []

                result: List[LoanItem] = []
                for i in range(row_count):
                    cells_raw = await rows.nth(i).locator("td, th").all_inner_texts()
                    cells = [_normalize(t) for t in cells_raw if _normalize(t)]
                    if not cells:
                        continue

                    joined = " ".join(cells).lower()
                    if "조회 결과가 없습니다" in joined or "검색 결과가 없습니다" in joined or "no data" in joined:
                        continue

                    title = _pick_title(cells, title_idx)
                    due_date = _pick_due_date(cells, due_idx)
                    status = _pick_status(cells, status_idx)
                    if not status and due_date:
                        # On "my loans" page, rows with due date are active loans by definition.
                        status = "대출중"

                    # A real loan row must include at least one date-like value.
                    if not due_date:
                        continue

                    result.append(
                        LoanItem(
                            title=title or "(title parse failed)",
                            due_date=due_date or "(due date parse failed)",
                            status=status or "상태 확인 필요",
                        )
                    )

                log.info("Scrape complete: %d rows", len(result))
                return result
            finally:
                await context.close()
                await browser.close()
    except PermissionError as e:
        raise RuntimeError(
            "Playwright process launch was blocked by OS permissions (WinError 5). "
            "Run terminal/IDE as Administrator or allow python/node subprocess creation in security policy."
        ) from e
    except PlaywrightTimeoutError as e:
        raise RuntimeError(f"Playwright timeout: {e}") from e


async def main() -> None:
    items = await login_and_scrape()

    if not items:
        print("\nNo current loans found.\n")
        return

    print()
    print(f"{'#':>3}  {'Title':<40}  {'Due Date':<14}  {'Status'}")
    print("-" * 90)
    for idx, item in enumerate(items, 1):
        print(f"{idx:>3}  {item.title:<40}  {item.due_date:<14}  {item.status}")
    print("-" * 90)
    print(f"Total: {len(items)}")

    payload = [asdict(item) for item in items]
    print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    asyncio.run(main())
