# 배포 가이드 - Mock 데이터 제거 및 실제 API 연동

## 변경 사항

### 1. Mock 데이터 제거
- `data.sql` 파일에서 모든 Mock 샘플 데이터 제거
- 실제 외부 API(알라딘, 카카오)에서만 데이터를 가져오도록 변경

### 2. 이미지 URL HTTPS 변환
- HTTP로 제공되는 이미지 URL을 HTTPS로 자동 변환하여 Mixed Content 문제 해결
- `BookDataLoaderService`에서 이미지 저장 시 HTTPS 변환 적용

### 3. 데이터 로딩 개선
- 초기 로딩 목표: 200권 (기존 100권에서 증가)
- DB에 50권 미만일 때 자동으로 외부 API에서 데이터 로드
- 더 다양한 키워드 추가 (40+ 키워드)

### 4. 프론트엔드 이미지 에러 처리 개선
- 이미지 로드 실패 시 fallback 이미지 자동 적용
- 무한 재시도 방지 로직 추가
- 관련 도서 이미지에도 에러 처리 추가

## DB 초기화 및 재시작 방법

### Docker 환경에서 DB 초기화

```powershell
# 1. 기존 컨테이너 중지 및 제거
docker-compose down

# 2. DB 볼륨 삭제 (모든 데이터 초기화)
docker volume rm com_pgdata

# 3. 컨테이너 재시작
docker-compose up -d

# 4. 로그 확인 (외부 API에서 데이터 로드 확인)
docker logs -f ydanawa-backend
```

### 로컬 개발 환경

```powershell
# 1. PostgreSQL DB 접속
psql -h localhost -p 5433 -U root -d ydanawa_db

# 2. books 테이블 데이터 삭제
DELETE FROM books;

# 3. 애플리케이션 재시작
# BookDataLoaderService가 자동으로 외부 API에서 데이터 로드
```

## 확인 사항

### 1. 외부 API 키 설정 확인

```powershell
# 환경 변수 확인
echo $env:KAKAO_REST_API_KEY
echo $env:ALADIN_TTB_KEY
```

환경 변수가 설정되어 있지 않으면 `application.yml`에서 기본값 사용

### 2. 데이터 로딩 확인

애플리케이션 시작 로그에서 다음과 같은 메시지 확인:

```
현재 DB에 저장된 도서 수: 0
DB에 도서 데이터가 부족합니다. 외부 API에서 실제 데이터를 가져옵니다...
'자바' 키워드로 도서 검색 중...
50권 저장 완료 (누적: 50권)
'파이썬' 키워드로 도서 검색 중...
...
목표 도서 수(200)에 도달했습니다.
총 200권의 도서를 외부 API에서 가져와 저장했습니다.
```

### 3. 이미지 확인

- 모든 도서 이미지가 HTTPS로 로드되는지 확인
- Mixed Content 경고가 없는지 브라우저 콘솔 확인
- 이미지 로드 실패 시 fallback 이미지(회색 그라디언트)가 표시되는지 확인

## 트러블슈팅

### 문제: 외부 API에서 데이터를 가져오지 못함

**원인**: API 키가 설정되지 않았거나 잘못됨

**해결**:
```powershell
# 환경 변수 설정
$env:KAKAO_REST_API_KEY="your-kakao-api-key"
$env:ALADIN_TTB_KEY="your-aladin-ttb-key"
```

### 문제: 이미지가 표시되지 않음

**원인**: Mixed Content (HTTP/HTTPS 혼용) 문제

**해결**: 이미 코드에서 자동으로 HTTPS 변환하므로, 브라우저 캐시를 삭제하고 새로고침

### 문제: DB에 데이터가 중복됨

**원인**: DB를 초기화하지 않고 재시작

**해결**: 위의 "DB 초기화 및 재시작 방법" 참조

## 성능 최적화

- **캐싱**: Spring Cache를 사용하여 외부 API 호출 최소화
- **배치 저장**: 50권씩 배치로 저장하여 DB 성능 향상
- **API 호출 간격**: 500ms 딜레이로 Rate Limit 방지
- **이미지 품질**: 썸네일 대신 중간 크기 이미지 사용 (더 선명한 이미지)

## 추가 개선 사항

1. **이미지 품질 향상**
   - 카카오: 120x174 → 480x696
   - 알라딘: cover1 → coversum

2. **에러 처리 강화**
   - 외부 API 실패 시 자동으로 다른 API 시도
   - 프론트엔드에서 이미지 로드 실패 시 fallback 처리

3. **데이터 품질**
   - ISBN이 있는 책만 저장
   - 중복 체크로 데이터 무결성 보장

