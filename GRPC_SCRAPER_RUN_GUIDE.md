# gRPC + FastAPI Scraper 실행 가이드

이 가이드는 `Kakao Book API -> FastAPI(gRPC Server) -> Spring Boot(gRPC Client) -> Vue` 흐름을 실행하는 절차입니다.

## 1) 사전 준비

- Docker Desktop 실행
- PowerShell 기준 작업 경로: `C:\yjudanawa-damo\com`
- 카카오 REST API 키 준비

## 2) 환경변수 설정

PowerShell에서 아래를 먼저 실행:

```powershell
cd C:\yjudanawa-damo\com
$env:KAKAO_REST_API_KEY="여기에_카카오_REST_API_KEY"
```

선택 설정(프록시 URL 외부 접근 기준 URL):

```powershell
$env:SCRAPER_PUBLIC_BASE_URL="http://localhost:8090"
```

## 3) Docker Compose로 전체 서비스 실행

```powershell
docker compose up --build -d
```

상태 확인:

```powershell
docker compose ps
```

로그 확인:

```powershell
docker compose logs -f library-scraper
docker compose logs -f backend
```

## 4) 네트워크/포트 확인 포인트

- FastAPI HTTP: `http://localhost:8090`
- gRPC: `localhost:9090` (컨테이너 내부 서비스명: `ydanawa-library-scraper:9090`)
- Spring Boot: `http://localhost:8080`
- Spring의 gRPC 대상 기본값:
  - `app.grpc.host=ydanawa-library-scraper`
  - `app.grpc.port=9090`

## 5) 동작 테스트

스크래퍼 헬스체크:

```powershell
Invoke-RestMethod -Uri "http://localhost:8090/health" -Method Get
```

Spring -> gRPC -> Kakao 검색 테스트:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/grpc/books/search?query=자바&page=1&size=10" -Method Get
```

응답 필드 확인:

- `title`
- `author`
- `isbn`
- `price`
- `kakaoThumbnailUrl`
- `imageUrl`

`imageUrl`가 `http://localhost:8090/proxy/image?...` 형태면 403 대응 프록시가 적용된 결과입니다.

## 6) 프록시 이미지 직접 테스트

```powershell
$u = [System.Web.HttpUtility]::UrlEncode("https://search1.kakaocdn.net/...")
Invoke-WebRequest -Uri "http://localhost:8090/proxy/image?url=$u" -OutFile ".\proxy-test.jpg"
```

## 7) 재기동/종료

재빌드 포함 재기동:

```powershell
docker compose up --build -d
```

종료:

```powershell
docker compose down
```

데이터 볼륨까지 삭제:

```powershell
docker compose down -v
```

## 8) 로컬 개발 모드(선택)

### 8-1) Python 스크래퍼 단독 실행

```powershell
cd C:\yjudanawa-damo\com\library-scraper
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python -m grpc_tools.protoc -I./proto --python_out=. --grpc_python_out=. ./proto/library.proto
$env:KAKAO_REST_API_KEY="여기에_카카오_REST_API_KEY"
$env:GRPC_PORT="9090"
$env:HTTP_PORT="8090"
python .\grpc_server.py
```

### 8-2) Spring Boot 단독 실행

다른 터미널에서:

```powershell
cd C:\yjudanawa-damo\com
$env:APP_GRPC_HOST="localhost"
$env:APP_GRPC_PORT="9090"
.\gradlew.bat bootRun
```

## 9) 트러블슈팅

- `query is required`:
  - `/api/grpc/books/search` 호출 시 `query` 파라미터 누락 여부 확인
- `KAKAO_REST_API_KEY is not configured`:
  - `KAKAO_REST_API_KEY` 환경변수 설정 확인 후 컨테이너 재시작
- Spring에서 gRPC 연결 실패:
  - Docker 실행 시 `APP_GRPC_HOST=ydanawa-library-scraper`, `APP_GRPC_PORT=9090` 확인
  - 로컬 실행 시 `APP_GRPC_HOST=localhost`로 변경 필요
- 이미지가 프론트에서 차단:
  - 응답의 `imageUrl`가 프록시 URL인지 확인
  - 프록시 URL이 아니고 403이면 스크래퍼 로그 확인
