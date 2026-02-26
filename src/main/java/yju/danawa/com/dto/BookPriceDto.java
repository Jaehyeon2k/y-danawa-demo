package yju.danawa.com.dto;

public record BookPriceDto(
    String store,
    String storeName,
    Integer price,
    String url,
    String deliveryInfo,
    boolean available
) {
    public static BookPriceDto unavailable(String store, String storeName, String url) {
        return new BookPriceDto(store, storeName, null, url, null, false);
    }
}

