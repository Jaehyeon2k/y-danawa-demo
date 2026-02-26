package yju.danawa.com.service.dto;

public record AladinItem(
        String title,
        String author,
        String publisher,
        String cover,
        String isbn,
        String isbn13,
        Integer priceSales
) {
}
