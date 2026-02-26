# Y-Danawa Frontend (Vue + TypeScript)

## Quick Start

```powershell
cd frontend
npm install
npm run dev
```

## Type Check

```powershell
npm run typecheck
```

## Book Detail Page

The root route renders `BookDetailPage.vue` to showcase the 3-column layout and detail UI.

## ISBN Lookup Demo

```powershell
npm run isbn:demo -- 9780306406157
```

### Optional API Keys

```powershell
# Windows PowerShell
$env:ALADIN_API_KEY="your-ttb-key"
$env:KAKAO_REST_API_KEY="your-kakao-rest-key"
```

## API Base URL

The Axios base URL is `/api` (assumes Nginx reverse proxy). Update the backend proxy if needed.
