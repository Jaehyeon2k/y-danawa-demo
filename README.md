# Y-Danawa

영진전문대학교 도서 통합 검색 플랫폼

## 주요 기능

- **도서 검색**: PostgreSQL DB + 외부 API(알라딘/카카오/구글)
- **가격 비교**: YES24, 알라딘, 교보문고, 인터파크 실시간 크롤링
- **도서관 소장 확인**: Playwright로 영진전문대 도서관 대출 상태 조회
- **이미지 조회**: 알라딘(고화질) -> 카카오 -> 구글 -> 플레이스홀더 Fallback

---

## 팀원 환경 설정 가이드

### 1단계: 루트 API 키 설정 (`com/.env`)

```powershell
cd com
Copy-Item .env.example .env
notepad .env
```

`com/.env` 파일에 팀 공용 API 키를 입력합니다:

```env
KAKAO_REST_API_KEY=your_kakao_rest_api_key
ALADIN_TTB_KEY=your_aladin_ttb_key
GOOGLE_API_KEY=your_google_api_key
```

API 키 발급처:
- Kakao: https://developers.kakao.com/ (REST API 키)
- Aladin: https://www.aladin.co.kr/ttb/ (TTB 키)
- Google: https://console.cloud.google.com/ (Books API)

### 2단계: 도서관 스크래퍼 설정 (`com/library-scraper/.env`)

```powershell
cd com/library-scraper
Copy-Item .env.example .env
notepad .env
```

**각자의 학번과 비밀번호**를 입력합니다:

```env
LIB_USER_ID=2501203
LIB_USER_PASSWORD=your_actual_password
```

> **API 키는 이 파일에 입력하지 않아도 됩니다.**
> 루트 `com/.env`에 설정한 키가 자동으로 상속됩니다.
> 이 파일의 API 키 줄이 주석 처리(`#`)되어 있어야 루트 값이 덮어씌워지지 않습니다.

### 3단계: .gitignore 확인

`.env` 파일이 절대 커밋되지 않도록 다음이 `com/.gitignore`에 포함되어 있는지 확인하세요:

```gitignore
.env
.env.*
!.env.example
!.env.*.example
```

`library-scraper/.gitignore`에도 `.env`가 포함되어 있어야 합니다.

```powershell
# .env가 추적되지 않는지 확인
git status
```

> 비밀번호나 API 키가 포함된 `.env` 파일은 절대 커밋하지 마세요!

---

## Docker로 빠른 시작 (권장)

### 사전 준비
1. [Docker Desktop](https://www.docker.com/products/docker-desktop) 설치 및 실행
2. 위의 `.env` 파일 설정 완료

### 실행

```powershell
# 자동 실행 스크립트
.\docker-run.ps1

# 또는 수동 실행
.\gradlew.bat clean bootJar -x test
docker-compose up --build -d
```

### 접속 정보
- **프론트엔드**: http://localhost
- **백엔드 API**: http://localhost:8080/api
- **도서관 스크래퍼**: http://localhost:8090
- **데이터베이스**: localhost:5433 (ydanawa_db)

### Docker 명령어

```powershell
docker-compose logs -f              # 전체 로그
docker-compose logs -f backend      # 백엔드만
docker-compose logs -f library-scraper  # 스크래퍼만
docker-compose down                 # 중지
docker-compose down -v              # 중지 + DB 삭제
```

---

## 로컬 개발 모드

Docker 없이 개발할 때:

### 1. DB만 Docker로

```powershell
docker-compose up db -d
```

### 2. 도서관 스크래퍼 (Python)

```powershell
cd library-scraper
pip install -r requirements.txt
playwright install chromium
python -m grpc_tools.protoc -I./proto --python_out=. --grpc_python_out=. ./proto/library.proto
python run_servers.py
```

### 3. 백엔드 (Spring Boot)

```powershell
cd com
.\gradlew.bat bootRun
```

### 4. 프론트엔드 (Vue 3)

```powershell
cd com/frontend
npm install
npm run dev
```

---

## 기술 스택

| 레이어 | 기술 |
|--------|------|
| Frontend | Vue 3 + TypeScript + Vite + Tailwind CSS |
| Backend | Spring Boot 4.0.2 (Java 21) |
| Scraper | FastAPI + Playwright + gRPC (Python) |
| Database | PostgreSQL 16 (pgvector) |
| 통신 | gRPC (protobuf), REST API |
| 배포 | Docker Compose |

## API 엔드포인트

| 경로 | 설명 |
|------|------|
| `GET /api/books/search?q=` | 도서 검색 |
| `GET /api/books/library-check?isbn=` | 도서관 소장 확인 |
| `GET /api/books/prices?isbn=` | 가격 비교 |
| `GET /api/books/info?isbn13=` | gRPC 도서 정보 (이미지+상태) |
| `GET /api/books/infinite?cursor=&limit=` | 무한 스크롤 |
| `POST /api/auth/register` | 회원가입 |
| `POST /api/auth/login` | 로그인 (JWT) |

---

## 문제 해결

### 502 Bad Gateway
- `com/.env`에 API 키가 올바르게 설정되어 있는지 확인
- `library-scraper/.env`의 API 키 줄이 주석 처리(`#`)되어 있는지 확인
- 스크래퍼 서비스가 실행 중인지 확인: `curl http://localhost:8090/health`

### 이미지가 나오지 않음
- 알라딘/카카오 API 키가 `com/.env`에 설정되어 있는지 확인
- 이미지 없는 경우 플레이스홀더가 자동 반환됨

### 도서관 대출 상태 오류
- `library-scraper/.env`에 학번/비밀번호가 올바른지 확인
- `STATUS_WAIT_TIMEOUT_MS`를 늘려 느린 네트워크에 대응 (기본 12초)

---

## ������ env ���̵� (�߿�)

### 1) ���� ����/Ű�� `.env`���� ����

- Ŀ�� ����: `com/.env.example`, `com/library-scraper/.env.example`
- Ŀ�� ����: `com/.env`, `com/library-scraper/.env`

```powershell
cd com
Copy-Item .env.example .env
cd library-scraper
Copy-Item .env.example .env
```

### 2) ���� ���� ���ø�

- Spring/Java (`com/.env.example`)
  - `KAKAO_REST_API_KEY`
  - `ALADIN_TTB_KEY`
  - `GOOGLE_API_KEY`
  - `APP_GRPC_HOST`
  - `APP_GRPC_PORT` (�⺻ `9090`)
  - `PLACEHOLDER_IMAGE_URL`
- FastAPI/��ũ���� (`com/library-scraper/.env.example`)
  - `LIB_USER_ID`
  - `LIB_USER_PASSWORD`
  - `GRPC_PORT` (�⺻ `9090`)
  - `STATUS_WAIT_TIMEOUT_MS`

### 3) ��Ʈ ����ġ�� ���� 502 ����

- `APP_GRPC_PORT` (Spring)�� `GRPC_PORT` (library-scraper)�� �����ϰ� ����
- �⺻���� ��� `9090`
- Docker ��� �� `compose.yaml`�� ���� ��Ʈ�� ����
