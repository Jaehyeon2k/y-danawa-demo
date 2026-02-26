package yju.danawa.com.dto;

import java.time.LocalDate;
import java.util.List;

public record BookSearchItemDto(
        Long id,
        String title,
        String titleNorm,
        String isbn13,
        String authors,
        String publisher,
        LocalDate publishedAt,
        List<String> imageUrls) {
}
