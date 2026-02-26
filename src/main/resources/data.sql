-- Mock 데이터 제거: 외부 API(알라딘, 카카오)에서만 실제 도서 데이터를 가져옵니다
-- BookDataLoaderService가 자동으로 외부 API에서 데이터를 로드합니다

-- 빈 파일 방지용 (Spring이 빈 SQL 파일을 에러로 처리하므로 최소 1개의 SQL 문 필요)
SELECT 1;

