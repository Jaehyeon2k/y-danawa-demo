package yju.danawa.com.dto;

import java.util.List;

/**
 * 무한 스크롤 응답 DTO
 * Cursor-based Pagination을 위한 제네릭 응답 클래스
 * 
 * @param <T> 데이터 타입 (예: BookDto)
 */
public record InfiniteScrollResponse<T>(
        List<T> data,
        String nextCursor,
        boolean hasMore) {
    /**
     * 팩토리 메서드
     * 
     * @param data  현재 페이지 데이터
     * @param limit 요청한 limit
     * @return InfiniteScrollResponse 객체
     */
    public static <T> InfiniteScrollResponse<T> of(List<T> data, int limit) {
        boolean hasMore = data.size() >= limit;
        String nextCursor = hasMore && !data.isEmpty() ? extractIsbn(data.get(data.size() - 1)) : null;

        return new InfiniteScrollResponse<>(data, nextCursor, hasMore);
    }

    /**
     * 마지막 아이템에서 ISBN 추출
     */
    private static <T> String extractIsbn(T item) {
        if (item instanceof yju.danawa.com.dto.BookDto bookDto) {
            return bookDto.isbn();
        }
        return null;
    }
}
