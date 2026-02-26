from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from typing import Optional

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app_service import LibraryBackendService
from config import get_settings, validate_runtime_settings
from library_search_engine import LibrarySessionCrawler


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

settings = get_settings()
service = LibraryBackendService(settings)
crawler = LibrarySessionCrawler()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await crawler.start()
    app.state.library_crawler = crawler
    yield
    await crawler.close()

app = FastAPI(title="Y-Danawa Library Scraper API", version="2.0.0", lifespan=lifespan)


class LibraryCheckRequest(BaseModel):
    isbn: Optional[str] = None
    title: Optional[str] = None
    author: Optional[str] = None


class ImageResolveRequest(BaseModel):
    isbn: Optional[str] = None
    title: Optional[str] = None
    author: Optional[str] = None

class BookInfoRequest(BaseModel):
    isbn13: str

class EbookRequest(BaseModel):
    title: str
    author: Optional[str] = None
    publisher: Optional[str] = None


@app.get("/")
async def root():
    return {"status": "ok", "service": "library-scraper", "version": "2.0.0"}


@app.get("/health")
async def health():
    return {"status": "healthy", "grpc_port": settings.grpc_port}


@app.post("/check-library")
async def check_library(request: LibraryCheckRequest):
    if not request.isbn and not request.title:
        raise HTTPException(status_code=400, detail="isbn or title is required")

    result = await service.check_library(isbn=request.isbn, title=request.title, author=request.author)
    return result


@app.post("/resolve-image")
async def resolve_image(request: ImageResolveRequest):
    if not request.isbn and not request.title:
        raise HTTPException(status_code=400, detail="isbn or title is required")

    return await service.resolve_book_image(isbn=request.isbn, title=request.title, author=request.author)


@app.get("/books/search")
async def search_books(query: str, page: int = 1, size: int = 20):
    return {"books": await service.search_books(query=query, page=page, size=size)}

@app.post("/book-info")
async def book_info(request: BookInfoRequest):
    return await service.get_book_info_by_isbn13(request.isbn13)

@app.get("/book-info")
async def book_info_query(isbn13: str):
    return await service.get_book_info_by_isbn13(isbn13)

@app.get("/books/info")
async def book_info_legacy(isbn13: str):
    return await service.get_book_info_by_isbn13(isbn13)

@app.post("/check-ebook")
async def check_ebook(request: EbookRequest):
    if not request.title or not request.title.strip():
        raise HTTPException(status_code=400, detail="title is required")
    return await service.check_ebook(request.title, request.author, request.publisher)


@app.get("/library/search")
async def search_library(title: str):
    if not title or not title.strip():
        raise HTTPException(status_code=400, detail="title is required")
    try:
        return await asyncio.wait_for(app.state.library_crawler.search(title), timeout=12)
    except asyncio.TimeoutError:
        return {
            "query_title_raw": title,
            "query_title_clean": "",
            "matched_title": "",
            "material_type": "unknown",
            "status": "\uc815\ubcf4 \uc5c6\uc74c/\ubbf8\uc18c\uc7a5",
            "holding_count": 0,
            "reason": "search_timeout_12s",
            "error_type": "timeout",
        }


if __name__ == "__main__":
    validate_runtime_settings(settings)
    uvicorn.run(app, host=settings.http_host, port=settings.http_port, log_level="info")
