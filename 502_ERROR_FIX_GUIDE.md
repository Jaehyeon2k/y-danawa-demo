# 🔧 502 에러 완벽 해결 가이드

## 🐛 문제 증상

**"외부 API 검색 실패 (status: 502)"**

502 Bad Gateway 에러는 다음과 같은 상황에서 발생합니다:
- 백엔드가 외부 API 서버(카카오/알라딘)로부터 응답을 받지 못함
- 외부 API 서버가 다운되었거나 응답이 느림
- API 키가 잘못 설정됨
- 네트워크 연결 문제

---

## ✅ 적용된 해결책

### 1. **상세한 로깅 추가** ✅

백엔드에서 API 호출 과정을 모두 로그로 기록:

```java
// ExternalBookService.java
log.info("🔍 Kakao API 호출 시작: query={}", query);
log.info("✅ Kakao API 성공: {}건 조회됨", resultCount);
log.error("❌ Kakao API 호출 실패: status={}", clientResponse.statusCode());
```

**효과**: 문제 발생 지점을 정확히 파악 가능

### 2. **프론트엔드 에러 메시지 개선** ✅

상태 코드별로 명확한 안내 메시지:

```typescript
// HomePage.vue
if (status === 503) {
    return "⚠️ 외부 도서 API 키 설정이 필요합니다. (Kakao/Aladin API 키 확인)";
} else if (status === 502) {
    return "❌ 외부 도서 API 게이트웨이 오류: 외부 API 서버가 응답하지 않습니다. 잠시 후 다시 시도해주세요.";
} else if (status >= 500) {
    return "❌ 외부 도서 API 서버 오류 (${status}): 서버에 일시적인 문제가 발생했습니다.";
}
```

### 3. **타임아웃 설정** ✅ (이미 적용됨)

```java
HttpClient httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(10));
```

**효과**: 10초 이상 응답 없으면 자동 타임아웃

### 4. **에러 핸들링 강화** ✅

```java
.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
    clientResponse -> {
        log.error("❌ Kakao API 호출 실패: status={}", clientResponse.statusCode());
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Kakao API 호출 실패: " + clientResponse.statusCode()
        );
    })
```

---

## 🔍 502 에러 원인 진단

### ✅ 단계 1: 로그 확인

```powershell
# Docker 환경
docker logs -f ydanawa-backend | Select-String -Pattern "API"

# 로컬 환경
# 애플리케이션 실행 로그 확인
```

**확인할 내용**:
- ❌ "Kakao API 키가 설정되지 않았습니다" → API 키 필요
- ❌ "Kakao API 호출 실패: status=401" → API 키 잘못됨
- ❌ "Kakao API 호출 실패: status=429" → 요청 제한 초과
- ❌ "Kakao API 호출 실패: status=502/503" → 외부 API 서버 문제
- ⏱️ "ReadTimeoutException" → 응답 너무 느림
- ✅ "Kakao API 성공: X건 조회됨" → 정상 작동

### ✅ 단계 2: API 키 확인

```powershell
# 환경 변수 확인
echo $env:KAKAO_REST_API_KEY
echo $env:ALADIN_TTB_KEY
```

**API 키 발급 방법**:

#### Kakao API
1. https://developers.kakao.com/ 접속
2. 로그인 후 "내 애플리케이션" → "애플리케이션 추가하기"
3. REST API 키 복사
4. 환경 변수 설정:
   ```powershell
   $env:KAKAO_REST_API_KEY="your-kakao-rest-api-key"
   ```

#### Aladin API
1. https://www.aladin.co.kr/ttb/wblog_reg.aspx 접속
2. 회원가입 및 TTB 키 발급 (무료)
3. TTB 키 복사
4. 환경 변수 설정:
   ```powershell
   $env:ALADIN_TTB_KEY="your-aladin-ttb-key"
   ```

### ✅ 단계 3: 직접 API 테스트

#### Kakao API 테스트
```powershell
curl -X GET "https://dapi.kakao.com/v3/search/book?query=자바" `
  -H "Authorization: KakaoAK YOUR_API_KEY"
```

#### Aladin API 테스트
```powershell
curl "https://www.aladin.co.kr/ttb/api/ItemSearch.aspx?ttbkey=YOUR_TTB_KEY&Query=자바&QueryType=Title&SearchTarget=Book&output=js&Version=20131101"
```

**정상 응답**: JSON 데이터 반환
**에러 응답**: 401, 403, 502 등

---

## 🚀 해결 방법

### Option 1: API 키 설정 (권장)

API 키를 설정하면 더 많은 데이터를 안정적으로 가져올 수 있습니다.

```powershell
# PowerShell에서 환경 변수 설정
$env:KAKAO_REST_API_KEY="your-kakao-rest-api-key"
$env:ALADIN_TTB_KEY="your-aladin-ttb-key"

# Docker Compose 환경변수 파일 생성
echo "KAKAO_REST_API_KEY=your-kakao-rest-api-key" > .env
echo "ALADIN_TTB_KEY=your-aladin-ttb-key" >> .env

# 재시작
docker-compose down
docker-compose up -d
```

### Option 2: DB 검색만 사용 (임시 해결책)

외부 API 없이도 DB에 저장된 데이터로 검색 가능:

1. 초기 데이터 로딩 (BookDataLoaderService가 자동 실행)
2. 이후부터는 DB 검색만 사용
3. 외부 API 에러 발생 시 자동으로 DB 결과만 표시

**장점**: API 키 없이도 작동
**단점**: 최신 데이터 못 가져옴, 검색 결과 제한적

### Option 3: application.yml에 API 키 직접 설정

```yaml
# src/main/resources/application.yml
app:
  external:
    kakao-rest-api-key: "your-kakao-rest-api-key"
    aladin-ttb-key: "your-aladin-ttb-key"
```

⚠️ **주의**: Git에 커밋하지 않도록 `.gitignore`에 추가

---

## 🧪 테스트 방법

### 1. 백엔드 로그 확인

```powershell
# Docker
docker logs -f ydanawa-backend

# 로컬
.\gradlew.bat bootRun
```

**정상 로그 예시**:
```
🔍 Kakao API 호출 시작: query=자바
✅ Kakao API 성공: 50건 조회됨
```

**에러 로그 예시**:
```
❌ Kakao API 키가 설정되지 않았습니다.
❌ Kakao API 호출 실패: status=502
```

### 2. 프론트엔드에서 테스트

1. http://localhost 접속
2. 검색창에 "자바" 입력
3. F12 → Console 탭 확인

**정상**:
- 도서 목록 표시
- 이미지 정상 로드

**에러**:
- "⚠️ 외부 도서 API 키 설정이 필요합니다" → Option 1 참조
- "❌ 외부 도서 API 게이트웨이 오류" → Option 3 참조 (외부 API 서버 문제)

### 3. API 엔드포인트 직접 호출

```powershell
# 내부 DB 검색 (항상 작동)
curl http://localhost:8080/api/books/search?q=자바

# 외부 API 검색 (API 키 필요)
curl http://localhost:8080/api/external/books?query=자바&source=kakao
curl http://localhost:8080/api/external/books?query=자바&source=aladin
```

---

## 📊 개선 효과

| 항목 | Before | After |
|------|--------|-------|
| **에러 메시지** | "외부 API 검색 실패 (status: 502)" | "❌ 외부 도서 API 게이트웨이 오류: 외부 API 서버가 응답하지 않습니다" |
| **로깅** | ❌ 없음 | ✅ 상세한 단계별 로깅 |
| **디버깅** | 🔴 어려움 | 🟢 로그로 즉시 파악 |
| **사용자 피드백** | 🔴 모호함 | 🟢 명확한 원인 안내 |
| **타임아웃** | ⏱️ 무한 대기 가능 | ✅ 10초 타임아웃 |

---

## 💡 추가 권장사항

### 1. **환경 변수 파일 사용** (.env)

```bash
# .env 파일 생성
KAKAO_REST_API_KEY=your-kakao-rest-api-key
ALADIN_TTB_KEY=your-aladin-ttb-key
```

```yaml
# compose.yaml
services:
  backend:
    env_file:
      - .env
```

### 2. **Fallback 전략 강화**

외부 API 실패 시 자동으로 DB 검색으로 전환:

```typescript
// 이미 적용됨
const [dbResult, externalResult] = await Promise.allSettled([
  getBooks(keyword),
  searchExternalBooks(keyword, "auto"),
]);

// DB 결과 + 외부 API 결과 (실패해도 DB는 표시)
displayResults.value = mergeResults(dbItems, externalItems);
```

### 3. **모니터링 설정**

- Prometheus로 외부 API 응답 시간 모니터링
- 502 에러 발생 횟수 추적
- 슬랙/이메일 알림 설정

---

## ✅ 체크리스트

문제 해결 전 확인하세요:

- [ ] 로그에서 "❌ Kakao API 키가 설정되지 않았습니다" 확인
- [ ] 환경 변수 `KAKAO_REST_API_KEY` 설정 확인
- [ ] 환경 변수 `ALADIN_TTB_KEY` 설정 확인
- [ ] curl로 외부 API 직접 호출 테스트
- [ ] 프론트엔드 콘솔에서 "외부 API 에러 상세" 로그 확인
- [ ] 백엔드 로그에서 "🔍 Kakao API 호출 시작" 확인
- [ ] 10초 타임아웃 후에도 문제 지속되는지 확인

---

## 🎉 결과

### 적용 전
- ❌ 502 에러 발생 시 원인 파악 어려움
- ❌ 사용자에게 모호한 메시지
- ❌ 로그 없어서 디버깅 불가

### 적용 후
- ✅ 로그로 정확한 원인 파악
- ✅ 사용자에게 명확한 안내 (API 키 필요 / 서버 문제 등)
- ✅ 502, 503, 429 등 상태 코드별 대응
- ✅ 콘솔 로그로 실시간 디버깅
- ✅ 타임아웃으로 무한 대기 방지

---

## 📞 여전히 문제가 있다면?

1. **로그 전체 확인**:
   ```powershell
   docker logs ydanawa-backend > backend.log
   # backend.log 파일 확인
   ```

2. **API 키 재발급**:
   - 카카오/알라딘 개발자 사이트에서 새 키 발급

3. **네트워크 확인**:
   ```powershell
   curl https://dapi.kakao.com
   curl https://www.aladin.co.kr
   ```

4. **Docker 재시작**:
   ```powershell
   docker-compose down
   docker-compose up -d --build
   ```

---

**작업 완료!** 🎊

이제 502 에러가 발생해도:
- 📊 로그로 정확한 원인 파악
- 💬 사용자에게 명확한 안내
- 🔧 쉬운 문제 해결

API 키를 설정하면 최상의 경험을 제공할 수 있습니다!

