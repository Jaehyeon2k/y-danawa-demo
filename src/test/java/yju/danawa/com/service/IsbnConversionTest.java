package yju.danawa.com.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ISBN-10 to ISBN-13 변환 테스트
 */
class IsbnConversionTest {

    private static final Logger log = LoggerFactory.getLogger(IsbnConversionTest.class);

    /**
     * ISBN-10을 ISBN-13으로 변환
     */
    private String convertIsbn10ToIsbn13(String isbn10) {
        if (isbn10 == null || isbn10.length() != 10) {
            return null;
        }

        try {
            // ISBN-10의 마지막 체크 디지트 제거하고 978 접두어 추가
            String base = "978" + isbn10.substring(0, 9);

            // ISBN-13 체크 디지트 계산
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                int digit = Character.getNumericValue(base.charAt(i));
                // 홀수 위치(0-based)는 3을 곱함
                sum += (i % 2 == 0) ? digit : digit * 3;
            }

            int checkDigit = (10 - (sum % 10)) % 10;
            String isbn13 = base + checkDigit;

            log.info("ISBN-10 -> ISBN-13 변환: {} -> {}", isbn10, isbn13);
            return isbn13;
        } catch (Exception e) {
            log.warn("ISBN-10 to ISBN-13 변환 실패: {}", isbn10, e);
            return null;
        }
    }

    @Test
    void testIsbn10ToIsbn13Conversion() {
        // 테스트 케이스 1: 8994492003 -> 9788994492001
        String result1 = convertIsbn10ToIsbn13("8994492003");
        log.info("Test 1: 8994492003 -> {}", result1);
        assertNotNull(result1);
        assertEquals(13, result1.length());
        assertTrue(result1.startsWith("978"));

        // 테스트 케이스 2: 8966260950 -> 9788966260959
        String result2 = convertIsbn10ToIsbn13("8966260950");
        log.info("Test 2: 8966260950 -> {}", result2);
        assertNotNull(result2);
        assertEquals("9788966260959", result2);

        // 테스트 케이스 3: 8994492046 -> 9788994492049
        String result3 = convertIsbn10ToIsbn13("8994492046");
        log.info("Test 3: 8994492046 -> {}", result3);
        assertNotNull(result3);
        assertEquals("9788994492049", result3);

        // 테스트 케이스 4: null 처리
        String result4 = convertIsbn10ToIsbn13(null);
        assertNull(result4);

        // 테스트 케이스 5: 잘못된 길이
        String result5 = convertIsbn10ToIsbn13("123");
        assertNull(result5);
    }

    @Test
    void testSpecificCase_8994492003() {
        // 사진에서 본 실제 케이스
        // YES24: 8994492003 (10자리)
        // 도서관: 9788994492001 93000 (13자리)

        String isbn10 = "8994492003";
        String result = convertIsbn10ToIsbn13(isbn10);

        log.info("실제 케이스 테스트:");
        log.info("  입력 (ISBN-10): {}", isbn10);
        log.info("  출력 (ISBN-13): {}", result);
        log.info("  기대값: 9788994492001");

        assertNotNull(result);
        assertEquals(13, result.length());
        assertEquals("9788994492001", result);
    }
}

