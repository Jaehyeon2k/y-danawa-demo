import asyncio
from config import get_settings
from scraper_service import LibraryScraper
import json
import re

async def main():
    settings = get_settings()
    settings.playwright_headless = True
    scraper = LibraryScraper(settings)
    
    print("Testing '클린 코드'...")
    res = await scraper.check_library(isbn=None, title="클린 코드", author=None)
    print(json.dumps(res, indent=2, ensure_ascii=False))

if __name__ == "__main__":
    asyncio.run(main())
