# .env Security Guide

## 1) Local secret file
- Use `library-scraper/.env` for real credentials only.
- Current required keys:
  - `LIB_USER_ID=2501203`
  - `LIB_USER_PASSWORD=utgm1237@`

## 2) GitHub upload safety
- `library-scraper/.gitignore` already excludes `.env`.
- Before push, verify:
  - `git status --ignored` shows `.env` under ignored files.
  - `.env.example` is tracked, `.env` is not tracked.

## 3) Template usage
- Copy template:
```powershell
Copy-Item .env.example .env
```
- Fill only local `.env` values.

## 4) If `.env` was committed by mistake
- Remove from tracking:
```powershell
git rm --cached library-scraper/.env
git commit -m "Remove .env from tracking"
```
- Rotate exposed credentials immediately.
