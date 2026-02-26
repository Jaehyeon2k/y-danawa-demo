package yju.danawa.com.dto;

import java.time.LocalDate;

public record BookDto(
                String isbn,
                String title,
                String author,
                String publisher,
                String imageUrl,
                LocalDate publishedDate,
                Double price) {
}
