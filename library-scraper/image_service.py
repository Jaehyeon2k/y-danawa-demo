from __future__ import annotations

import logging
import re
from typing import Optional, Tuple

import httpx

from config import Settings


logger = logging.getLogger(__name__)


class BookImageService:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def resolve_image_url(self, isbn: Optional[str], title: Optional[str], author: Optional[str]) -> Tuple[str, str]:
        isbn13 = self._normalize_isbn13(isbn)
        if not isbn13:
            return self.settings.placeholder_image_url, "PLACEHOLDER"

        # Fallback order (ISBN-13 only): Aladin -> Kakao -> Google -> Placeholder
        # 1) Aladin — ISBN-based lookup returns the highest resolution cover images.
        aladin = await self._fetch_from_aladin(isbn13)
        if aladin:
            return aladin, "ALADIN"

        # 2) Kakao
        kakao = await self._fetch_from_kakao(isbn13)
        if kakao:
            return kakao, "KAKAO"

        # 3) Google
        google = await self._fetch_from_google(isbn13)
        if google:
            return google, "GOOGLE"

        # 4) Placeholder — guaranteed non-404 URL.
        return self.settings.placeholder_image_url, "PLACEHOLDER"

    @staticmethod
    def _normalize_isbn13(isbn: Optional[str]) -> str:
        if not isbn:
            return ""
        normalized = re.sub(r"[^0-9]", "", isbn)
        return normalized if len(normalized) == 13 else ""

    async def _fetch_from_kakao(self, isbn: str) -> str:
        if not self.settings.kakao_rest_api_key:
            return ""
        params = {"query": isbn, "target": "isbn"}

        headers = {"Authorization": f"KakaoAK {self.settings.kakao_rest_api_key}"}

        try:
            async with httpx.AsyncClient(timeout=self.settings.external_api_timeout_sec) as client:
                resp = await client.get("https://dapi.kakao.com/v3/search/book", params=params, headers=headers)
                resp.raise_for_status()
                docs = resp.json().get("documents", [])
                if not docs:
                    return ""
                thumb = docs[0].get("thumbnail") or ""
                return self._normalize_url(thumb)
        except Exception as e:
            logger.warning("Kakao image lookup failed: %s", e)
            return ""

    async def _fetch_from_aladin(self, isbn: str) -> str:
        if not self.settings.aladin_ttb_key:
            return ""

        try:
            async with httpx.AsyncClient(timeout=self.settings.external_api_timeout_sec) as client:
                params = {
                    "ttbkey": self.settings.aladin_ttb_key,
                    "itemIdType": "ISBN13",
                    "ItemId": isbn,
                    "output": "js",
                    "Version": "20131101",
                    "Cover": "Big",
                }
                resp = await client.get("https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx", params=params)

                resp.raise_for_status()
                items = resp.json().get("item", [])
                if not items:
                    return ""
                cover = items[0].get("cover") or ""
                return self._normalize_url(cover)
        except Exception as e:
            logger.warning("Aladin image lookup failed: %s", e)
            return ""

    async def _fetch_from_google(self, isbn: str) -> str:
        params = {"q": f"isbn:{isbn}", "maxResults": 1}
        if self.settings.google_api_key:
            params["key"] = self.settings.google_api_key

        try:
            async with httpx.AsyncClient(timeout=self.settings.external_api_timeout_sec) as client:
                resp = await client.get("https://www.googleapis.com/books/v1/volumes", params=params)
                resp.raise_for_status()
                items = resp.json().get("items", [])
                if not items:
                    return ""
                info = items[0].get("volumeInfo", {})
                image_links = info.get("imageLinks", {})
                url = image_links.get("thumbnail") or image_links.get("smallThumbnail") or ""
                return self._normalize_url(url)
        except Exception as e:
            logger.warning("Google image lookup failed: %s", e)
            return ""

    @staticmethod
    def _normalize_url(url: str) -> str:
        if not url:
            return ""
        return url.replace("http://", "https://")
