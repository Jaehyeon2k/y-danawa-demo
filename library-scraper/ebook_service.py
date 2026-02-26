"""
YJU Ebook Library search service (ebook.yjc.ac.kr).

The ebook library uses **EUC-KR (CP949)** encoding for both request
parameters and response HTML.  UTF-8 encoded search queries will
silently return zero results, so the encoding must be handled explicitly.

Key design decisions:
  - URL encoding uses ``urllib.parse.quote(keyword.encode('euc-kr'))``
  - HTTP response is decoded as ``euc-kr`` (with ``errors='replace'``)
  - Deep-link URL is returned so the user can click through to results
  - Holdings count (보유 N) is parsed from the HTML
  - Results are cached with an LRU-style OrderedDict
"""
from __future__ import annotations

import ipaddress
import logging
import re
import time
from collections import OrderedDict
from typing import Any, Dict, List, Optional, TYPE_CHECKING
from urllib.parse import quote, urlparse

import httpx

if TYPE_CHECKING:
    from config import Settings

logger = logging.getLogger(__name__)

_MAX_CACHE = 500
_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)


class EbookService:
    """Search ``ebook.yjc.ac.kr`` with correct EUC-KR encoding."""

    def __init__(self, settings: "Settings") -> None:
        self.settings = settings
        self._cache: OrderedDict[str, tuple[float, List[Dict[str, Any]]]] = OrderedDict()
        self._ttl = settings.ebook_cache_ttl_sec

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------
    def build_search_url(self, keyword: str) -> str:
        """Build ebook deep-link URL with EUC-KR encoded keyword.

        >>> svc.build_search_url("자바 ORM")
        'https://ebook.yjc.ac.kr/search/?srch_order=total&src_key=%C0%DA%B9%D9+ORM'
        """
        try:
            encoded = quote(keyword.encode("euc-kr"))
        except (UnicodeEncodeError, LookupError):
            # Rare characters not in EUC-KR — fall back to UTF-8.
            encoded = quote(keyword.encode("utf-8"))
        return f"{self.settings.ebook_search_url_prefix}{encoded}"

    async def search(self, keyword: str) -> List[Dict[str, Any]]:
        """Search ebook library.  Returns list of parsed book entries."""
        keyword = (keyword or "").strip()
        if not keyword:
            return []

        cached = self._get_cache(keyword)
        if cached is not None:
            return cached

        url = self.build_search_url(keyword)

        if not self._is_allowed_url(url):
            logger.warning("ebook_url_blocked url=%s", url)
            return []

        try:
            async with httpx.AsyncClient(
                timeout=10.0,
                follow_redirects=True,
                headers={"User-Agent": _UA},
            ) as client:
                resp = await client.get(url)
                resp.raise_for_status()

                # Decode response as EUC-KR (the server sends Content-Type
                # with charset=euc-kr, but httpx may not always honour it).
                try:
                    html = resp.content.decode("euc-kr", errors="replace")
                except Exception:
                    html = resp.text

            results = self._parse_results(html, url)
            self._set_cache(keyword, results)
            logger.info(
                "ebook_search keyword=%s url=%s results=%d",
                keyword, url, len(results),
            )
            return results

        except Exception as e:
            logger.warning("ebook_search_error keyword=%s error=%s", keyword, e)
            return []

    # ------------------------------------------------------------------
    # HTML parsing
    # ------------------------------------------------------------------
    @staticmethod
    def _parse_results(html: str, search_url: str) -> List[Dict[str, Any]]:
        """Parse ebook search result HTML and extract book entries.

        The ebook site typically renders a table or list with columns:
        title, author/publisher, holdings count.

        We extract:
          - title
          - author
          - holdings_total (보유 N)
          - detail_url  (deep link per book, if available)
        """
        results: list[dict[str, Any]] = []

        # ------ Parse individual book entries ------
        # Pattern 1: <a ...>title</a> ... 보유 N
        # Many legacy Korean library sites use <td> cells or <li> items.
        # We look for title links followed by holdings info.

        # Try to extract titles from common patterns
        title_patterns = [
            # <a href="..." class="...">Title</a>
            re.compile(
                r'<a\s+[^>]*href=["\']([^"\']*)["\'][^>]*>\s*'
                r'<(?:strong|b|span)[^>]*>([^<]+)</(?:strong|b|span)>\s*</a>',
                re.IGNORECASE,
            ),
            # Simple: <td class="title"><a href="...">Title</a></td>
            re.compile(
                r'<a\s+[^>]*href=["\']([^"\']+)["\'][^>]*>([^<]{2,80})</a>',
                re.IGNORECASE,
            ),
        ]

        # Find all titles with their links
        entries: list[tuple[str, str]] = []  # (detail_url, title)
        seen_titles: set[str] = set()
        for pat in title_patterns:
            for m in pat.finditer(html):
                href, title = m.group(1).strip(), m.group(2).strip()
                if not title or len(title) < 2:
                    continue
                # Skip navigation/header links
                if any(skip in title for skip in ("로그인", "회원가입", "이용안내", "공지사항", "메인")):
                    continue
                norm = title.lower()
                if norm not in seen_titles:
                    seen_titles.add(norm)
                    entries.append((href, title))

        # ------ Parse holdings counts ------
        # Pattern: "보유" followed by digits (possibly with spaces/colons)
        holdings_all = re.findall(r'보유\s*[:\s]*(\d+)', html)
        total_holdings = sum(int(h) for h in holdings_all) if holdings_all else 0

        if entries:
            for i, (href, title) in enumerate(entries[:20]):
                holding_count = int(holdings_all[i]) if i < len(holdings_all) else 0
                results.append({
                    "title": title,
                    "detail_url": href if href.startswith("http") else "",
                    "holdings_count": holding_count,
                    "source": "ebook",
                })
        elif total_holdings > 0:
            # Could not parse individual entries but found holdings
            results.append({
                "title": "(검색 결과 있음)",
                "detail_url": search_url,
                "holdings_count": total_holdings,
                "source": "ebook",
            })

        # Always include the search deep link for user reference
        return [{
            "search_url": search_url,
            "total_results": len(results),
            "total_holdings": total_holdings,
            "books": results,
        }]

    # ------------------------------------------------------------------
    # Security
    # ------------------------------------------------------------------
    def _is_allowed_url(self, url: str) -> bool:
        try:
            parsed = urlparse(url)
            if parsed.scheme not in ("http", "https"):
                return False
            host = (parsed.hostname or "").lower()
            if not host:
                return False
            try:
                ip = ipaddress.ip_address(host)
                if ip.is_private or ip.is_loopback or ip.is_link_local:
                    return False
            except ValueError:
                pass
            allowed = set(self.settings.allowed_ebook_hosts)
            return host in allowed
        except Exception:
            return False

    # ------------------------------------------------------------------
    # Cache (LRU, bounded)
    # ------------------------------------------------------------------
    def _get_cache(self, key: str) -> Optional[List[Dict[str, Any]]]:
        entry = self._cache.get(key)
        if not entry:
            return None
        expires_at, data = entry
        if time.time() > expires_at:
            self._cache.pop(key, None)
            return None
        self._cache.move_to_end(key)
        return data

    def _set_cache(self, key: str, data: List[Dict[str, Any]]) -> None:
        self._cache[key] = (time.time() + self._ttl, data)
        self._cache.move_to_end(key)
        while len(self._cache) > _MAX_CACHE:
            self._cache.popitem(last=False)
