import asyncio
import re
from playwright.async_api import async_playwright

LOGIN='https://lib.yju.ac.kr/Cheetah/Login/Login'
SEARCH='https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/9788998139766'
ID='2501203'
PW='utgm1237@'

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(headless=True, args=['--no-sandbox'])
        c = await b.new_context()
        page = await c.new_page()
        page.set_default_timeout(10000)
        await page.goto(LOGIN, wait_until='domcontentloaded', timeout=10000)

        for s in ["#formText input[name='loginId']", "input[name='loginId']", "input[name='id']"]:
            if await page.locator(s).count() > 0:
                await page.fill(s, ID)
                break

        for s in ["#formText input[name='loginpwd']", "input[name='loginpwd']", "input[type='password']"]:
            if await page.locator(s).count() > 0:
                await page.fill(s, PW)
                break

        for s in ["#formText button[type='submit']", "button[type='submit']", "input[type='submit']"]:
            if await page.locator(s).count() > 0:
                await page.locator(s).first.click()
                break

        try:
            await page.wait_for_load_state('networkidle', timeout=10000)
        except Exception:
            pass

        await page.goto(SEARCH, wait_until='domcontentloaded', timeout=10000)
        try:
            await page.wait_for_load_state('networkidle', timeout=10000)
        except Exception:
            pass

        selectors = ['.bookinfo', 'li.mb-4', 'div.bookinfo', 'table tbody tr', 'ul > li']
        for sel in selectors:
            cnt = await page.locator(sel).count()
            if cnt <= 0:
                continue
            print('SEL', sel, 'CNT', cnt)
            for i in range(min(cnt, 20)):
                t = (await page.locator(sel).nth(i).inner_text()).strip()
                h = await page.locator(sel).nth(i).inner_html()
                t = re.sub(r'\s+', ' ', t)
                print('ROW', i, t[:240])
                keys = ['단행본', '전자책', '대출가능', '대출중', '보유', 'icon', 'ico', 'badge', 'alt=', 'title=']
                hit = [k for k in keys if (k in t or k in h)]
                if hit:
                    print('HIT', hit)
            break

        await b.close()

asyncio.run(main())
