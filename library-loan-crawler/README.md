# 학교 도서관 대출 현황 크롤러

Playwright(async/await) 기반으로 학교 도서관에 로그인한 뒤,
대출 현황 페이지에서 **제목 / 반납일 / 상태**를 자동으로 수집합니다.

---

## 1. 파일 구성

```
library-loan-crawler/
├── main.py          ← 크롤러 실행 코드 (학번·비번은 여기에 없음)
├── .env             ← 개인 계정 정보 저장 (절대 커밋 금지!)
├── .env.example     ← 환경변수 템플릿 (커밋 가능)
├── .gitignore       ← .env 커밋 방지 설정
└── README.md        ← 이 문서
```

> **핵심 원칙**: `main.py`에는 학번·비밀번호가 **한 글자도 없습니다.**
> 모든 민감 정보는 `.env` 파일에만 존재하고, `.env`는 Git에 올라가지 않습니다.

---

## 2. 설치 및 실행

```bash
# 1) 이 디렉토리로 이동
cd com/library-loan-crawler

# 2) 가상환경 생성 및 활성화
python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # Mac/Linux

# 3) 패키지 설치
pip install playwright python-dotenv

# 4) Chromium 브라우저 설치 (최초 1회)
python -m playwright install chromium

# 5) .env 파일 생성 (아래 3번 참고)
copy .env.example .env          # Windows
# cp .env.example .env          # Mac/Linux

# 6) .env 에 본인 학번/비밀번호 입력 후 실행
python main.py
```

---

## 3. .env 작성 방법 (팀원별 개인 설정)

`.env.example`을 복사해서 `.env`를 만든 뒤, 아래 **두 줄만** 본인 정보로 수정하세요.

```env
# ── 필수: 본인 학번과 비밀번호 ──
LIB_USER_ID=본인학번
LIB_USER_PASSWORD=본인비밀번호

# ── 도서관 URL (학교가 같으면 수정 불필요) ──
LIB_LOGIN_URL=https://lib.yju.ac.kr
LIB_LOAN_URL=https://lib.yju.ac.kr/MyLibrary/Loan

# ── 선택 옵션 ──
PLAYWRIGHT_HEADLESS=true
PLAYWRIGHT_TIMEOUT_MS=30000
```

### 팀원이 수정해야 할 항목 정리

| 항목 | 파일 | 수정 여부 |
|------|------|----------|
| `LIB_USER_ID` | `.env` | **반드시 수정** (본인 학번) |
| `LIB_USER_PASSWORD` | `.env` | **반드시 수정** (본인 비밀번호) |
| `LIB_LOGIN_URL` | `.env` | 같은 학교면 그대로 |
| `LIB_LOAN_URL` | `.env` | 같은 학교면 그대로 |
| `main.py` | - | **수정 불필요** |

---

## 4. 보안 가이드 (깃허브 업로드 전 필수!)

### 4-1. 절대 하지 말아야 할 것

| 금지 사항 | 이유 |
|-----------|------|
| `.env` 파일을 `git add` 하는 것 | 학번·비밀번호가 GitHub에 공개됨 |
| `main.py`에 학번·비번 직접 입력 | 코드에 민감정보가 남음 |
| 스크린샷/로그에 비밀번호 포함 | PR이나 이슈에 노출 위험 |

### 4-2. 깃허브에 올리기 전 체크리스트

```
[ ] .env 파일이 .gitignore에 포함되어 있는가?
[ ] git status 에 .env 가 나타나지 않는가?
[ ] main.py 에 학번·비밀번호가 하드코딩되어 있지 않은가?
[ ] 커밋 로그(git log -p)에 비밀번호가 남아 있지 않은가?
[ ] 스크린샷이나 로그 파일에 비밀번호가 보이지 않는가?
```

### 4-3. 실수로 .env를 커밋했을 때 복구 방법

```bash
# 1) Git 추적에서 .env 제거 (로컬 파일은 유지)
git rm --cached .env

# 2) 새 커밋 생성
git commit -m "remove .env from tracking"

# 3) 이미 원격(GitHub)에 push 했다면:
#    - 즉시 비밀번호 변경 (학교 포털에서)
#    - Git 히스토리에서도 제거:
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch .env" \
  --prune-empty --tag-name-filter cat -- --all

# 4) 강제 push (팀원에게 사전 공지 필수)
git push --force
```

> **비밀번호가 한 번이라도 GitHub에 올라갔다면,
> 히스토리를 정리해도 반드시 비밀번호를 변경하세요.**

---

## 5. .gitignore 설정 확인

이 디렉토리의 `.gitignore`에 다음이 포함되어 있습니다:

```gitignore
# 환경변수 (민감정보)
.env
.env.*
!.env.example
```

또한 상위 `com/.gitignore`에도 동일한 규칙이 있어 **이중으로 보호**됩니다.

### 확인 방법

```bash
# .env 가 Git 추적 대상이 아닌지 확인
git status --ignored | grep .env
# → .env 가 "Ignored files" 섹션에 나오면 정상
```

---

## 6. 실행 결과 예시

```
14:30:01 [INFO] .env 로드 완료: .../library-loan-crawler/.env
14:30:01 [INFO] 대상: https://lib.yju.ac.kr
14:30:01 [INFO] headless=True, timeout=30000ms
14:30:02 [INFO] [1/3] 로그인 페이지 접속...
14:30:04 [INFO] [1/3] 로그인 완료
14:30:05 [INFO] [2/3] 대출현황 페이지 이동...
14:30:06 [INFO] [2/3] 페이지 로드 완료
14:30:06 [INFO] [3/3] 대출 목록 파싱...
14:30:06 [INFO] [3/3] 파싱 완료 — 2건

  #  제목                                      반납일          상태
--------------------------------------------------------------------------------
  1  혼자 공부하는 자바                          2025-03-01      대출중
  2  클린 코드                                  2025-02-28      대출중
--------------------------------------------------------------------------------
총 2건
```

---

## 7. 문제 해결 (FAQ)

| 증상 | 해결 방법 |
|------|----------|
| `환경변수 누락: LIB_USER_ID` | `.env` 파일이 없거나 값이 비어 있음 → `.env.example` 복사 후 수정 |
| `로그인 폼 요소를 찾을 수 없습니다` | 학교 사이트 구조가 변경됨 → `.env`의 셀렉터 값 수정 |
| `Playwright 타임아웃` | 네트워크 느림 → `PLAYWRIGHT_TIMEOUT_MS`를 60000 으로 증가 |
| `대출 목록이 비어 있습니다` | 대출 중인 도서가 없거나, `LIB_LOAN_URL`이 잘못됨 |
| `python-dotenv 미설치` | `pip install python-dotenv` 실행 |
