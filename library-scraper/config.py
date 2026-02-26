from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv


BASE_DIR = Path(__file__).resolve().parent
DOTENV_PATH = BASE_DIR / ".env"
ROOT_DOTENV_PATH = BASE_DIR.parent / ".env"

def _safe_load_dotenv(path: Path, override: bool = False) -> None:
    if not path.exists():
        return
    for enc in ("utf-8-sig", "cp949"):
        try:
            load_dotenv(path, encoding=enc, override=override)
            return
        except UnicodeDecodeError:
            continue


# Load root .env first, then service-local .env to allow local override.
_safe_load_dotenv(ROOT_DOTENV_PATH, override=False)
_safe_load_dotenv(DOTENV_PATH, override=True)


def _get_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _get_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def _get_str(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    if value is None:
        return default
    return value.strip()


def _get_str_any(names: list[str], default: str = "") -> str:
    for name in names:
        value = os.getenv(name)
        if value is not None and value.strip():
            return value.strip()
    return default


@dataclass(frozen=True)
class Settings:
    http_host: str
    http_port: int
    grpc_host: str
    grpc_port: int

    lib_base_url: str
    lib_login_url: str
    lib_search_url_prefix: str
    lib_user_id: str
    lib_user_password: str
    lib_id_selector: str
    lib_password_selector: str
    lib_submit_selector: str

    playwright_headless: bool
    playwright_timeout_ms: int
    status_wait_timeout_ms: int

    kakao_rest_api_key: str
    aladin_ttb_key: str
    google_api_key: str
    external_api_timeout_sec: int
    placeholder_image_url: str

    scraper_public_base_url: str
    allowed_library_hosts: tuple[str, ...]
    playwright_concurrency: int
    playwright_hard_timeout_ms: int
    rate_limit_window_sec: int
    rate_limit_max_requests: int
    ebook_base_url: str
    ebook_search_url_prefix: str
    allowed_ebook_hosts: tuple[str, ...]
    ebook_cache_ttl_sec: int
    ebook_result_wait_timeout_ms: int
    ebook_detail_wait_timeout_ms: int
    ebook_total_timeout_sec: int


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings(
        http_host=_get_str("HTTP_HOST", "0.0.0.0"),
        http_port=_get_int("HTTP_PORT", 8090),
        grpc_host=_get_str("GRPC_HOST", "0.0.0.0"),
        grpc_port=_get_int("GRPC_PORT", 9090),
        lib_base_url=_get_str("LIB_BASE_URL", "https://lib.yju.ac.kr"),
        lib_login_url=_get_str("LIB_LOGIN_URL", "https://lib.yju.ac.kr/Cheetah/Login/Login"),
        lib_search_url_prefix=_get_str(
            "LIB_SEARCH_URL_PREFIX", "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/"
        ),
        lib_user_id=_get_str_any(
            ["LIBRARY_ID", "LIB_USER_ID", "STUDENT_ID", "YJU_STUDENT_ID", "YJU_ID"],
            "",
        ),
        lib_user_password=_get_str_any(
            ["LIBRARY_PW", "LIB_USER_PASSWORD", "STUDENT_PASSWORD", "YJU_STUDENT_PASSWORD", "YJU_PASSWORD"],
            "",
        ),
        lib_id_selector=_get_str("LIB_ID_SELECTOR", "#formText input[name='loginId']"),
        lib_password_selector=_get_str("LIB_PASSWORD_SELECTOR", "#formText input[name='loginpwd']"),
        lib_submit_selector=_get_str("LIB_SUBMIT_SELECTOR", "#formText button[type='submit']"),
        playwright_headless=_get_bool("PLAYWRIGHT_HEADLESS", True),
        playwright_timeout_ms=_get_int("PLAYWRIGHT_TIMEOUT_MS", 10000),
        status_wait_timeout_ms=_get_int("STATUS_WAIT_TIMEOUT_MS", 10000),
        kakao_rest_api_key=_get_str_any(["KAKAO_REST_API_KEY", "KAKAO_API_KEY"], ""),
        aladin_ttb_key=_get_str_any(["ALADIN_TTB_KEY", "ALADIN_API_KEY"], ""),
        google_api_key=_get_str_any(["GOOGLE_API_KEY", "GOOGLE_BOOKS_API_KEY"], ""),
        external_api_timeout_sec=_get_int("EXTERNAL_API_TIMEOUT_SEC", 8),
        placeholder_image_url=_get_str(
            "PLACEHOLDER_IMAGE_URL",
            "https://placehold.co/120x174?text=%EC%9D%B4%EB%AF%B8%EC%A7%80%20%EC%A4%80%EB%B9%84%20%EC%A4%91",
        ),
        scraper_public_base_url=_get_str("SCRAPER_PUBLIC_BASE_URL", "http://localhost:8090"),
        allowed_library_hosts=tuple(
            h.strip().lower()
            for h in _get_str("ALLOWED_LIBRARY_HOSTS", "lib.yju.ac.kr").split(",")
            if h.strip()
        ),
        playwright_concurrency=_get_int("PLAYWRIGHT_CONCURRENCY", 4),
        playwright_hard_timeout_ms=_get_int("PLAYWRIGHT_HARD_TIMEOUT_MS", 6000),
        rate_limit_window_sec=_get_int("SCRAPER_RATE_LIMIT_WINDOW_SEC", 60),
        rate_limit_max_requests=_get_int("SCRAPER_RATE_LIMIT_MAX_REQUESTS", 60),
        ebook_base_url=_get_str("EBOOK_BASE_URL", "https://ebook.yjc.ac.kr"),
        ebook_search_url_prefix=_get_str(
            "EBOOK_SEARCH_URL_PREFIX",
            "https://ebook.yjc.ac.kr/search/?srch_order=total&src_key=",
        ),
        allowed_ebook_hosts=tuple(
            h.strip().lower()
            for h in _get_str("ALLOWED_EBOOK_HOSTS", "ebook.yjc.ac.kr").split(",")
            if h.strip()
        ),
        ebook_cache_ttl_sec=_get_int("EBOOK_CACHE_TTL_SEC", 900),
        ebook_result_wait_timeout_ms=_get_int("EBOOK_RESULT_WAIT_TIMEOUT_MS", 10000),
        ebook_detail_wait_timeout_ms=_get_int("EBOOK_DETAIL_WAIT_TIMEOUT_MS", 10000),
        ebook_total_timeout_sec=_get_int("EBOOK_TOTAL_TIMEOUT_SEC", 10),
    )


def validate_runtime_settings(settings: Settings) -> None:
    if not settings.lib_user_id:
        raise ValueError("LIBRARY_ID is required. Set it in library-scraper/.env")
    if not settings.lib_user_password:
        raise ValueError("LIBRARY_PW is required. Set it in library-scraper/.env")


# Module-level convenience alias — same cached instance as get_settings().
settings = get_settings()
