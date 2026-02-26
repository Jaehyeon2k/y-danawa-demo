# Y-Danawa Library Scraper (FastAPI + Playwright + gRPC)

ISBN 13자리를 핵심 키로 도서 상태/이미지를 통합 조회하는 서비스입니다.

## 핵심 동작

1. 이미지 조회 (ISBN13 우선)
- Aladin TTB API -> Kakao Book API -> Google Books API 순서로 조회
- 앞 API 실패/미검색 시 즉시 다음 API로 fallback
- 3개 API 모두 실패 시 `PLACEHOLDER_IMAGE_URL` 반환

2. 도서관 상태 조회 (Playwright)
- `.env`의 `LIB_USER_ID`, `LIB_USER_PASSWORD`로 로그인
- 검색 페이지에서 상태 텍스트 렌더링을 위해 최소 5초 대기 후 `wait_for_selector("text=대출가능")` / `wait_for_selector("text=대출중")` 수행
- 상태 매핑:
  - `대출가능` -> `AVAILABLE`
  - 그 외(대출중/미검출/미소장) -> `UNAVAILABLE`

3. gRPC + Spring Boot 연동
- `GetBookInfo(isbn13)` RPC 제공
- 응답 필드:
  - `isbn13`
  - `availability_status` (`AVAILABLE` | `UNAVAILABLE`)
  - `image_url`
  - `image_source` (`KAKAO` | `ALADIN` | `GOOGLE` | `PLACEHOLDER`)

## 파일 구조

- `config.py`: 환경 변수 중앙 관리 (`python-dotenv`)
- `image_service.py`: ISBN13 기반 이미지 fallback
- `scraper_service.py`: 로그인 + 도서 상태 크롤링
- `app_service.py`: 통합 서비스 레이어
- `grpc_server.py`: gRPC 서버
- `main.py`: FastAPI 서버
- `proto/library.proto`: gRPC 인터페이스

## 환경 변수 (.env)

`.env.example`을 복사해 사용:

```powershell
Copy-Item .env.example .env
```

필수:
- `LIB_USER_ID`
- `LIB_USER_PASSWORD`

선택(이미지 품질 향상):
- `KAKAO_REST_API_KEY`
- `ALADIN_TTB_KEY`
- `GOOGLE_API_KEY`

플레이스홀더:
- `PLACEHOLDER_IMAGE_URL`

## 보안 가이드

- 학번/비밀번호/API 키를 Python 코드에 하드코딩하지 마세요.
- 반드시 `library-scraper/.env`에만 저장하세요.
- 커밋 전 `.env`가 추적되지 않는지 확인하세요.
  - `library-scraper/.gitignore`에 `.env` 포함
  - 루트 `.gitignore`도 `.env` 제외 설정 포함
- 상세 절차: `ENV_SECURITY.md`

## 실행

```powershell
pip install -r requirements.txt
python -m grpc_tools.protoc -I./proto --python_out=. --grpc_python_out=. ./proto/library.proto
python grpc_server.py
```

## Spring Boot 호출 예시

- gRPC: `GetBookInfo(isbn13)`
- REST 브릿지: `GET /api/grpc/books/info?isbn13=9788994492001`

## /check-ebook 빠른 검증

로컬 FastAPI 서버 실행 후 아래 스크립트로 전자책 응답/지연시간을 바로 확인할 수 있습니다.

```powershell
cd com/library-scraper
powershell -ExecutionPolicy Bypass -File .\scripts\verify-check-ebook.ps1
```

옵션:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-check-ebook.ps1 -BaseUrl "http://localhost:8090" -RequestTimeoutSec 12
```
