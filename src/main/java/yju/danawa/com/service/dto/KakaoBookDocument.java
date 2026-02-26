package yju.danawa.com.service.dto;

import java.util.List;

public record KakaoBookDocument(
        String title,
        List<String> authors,
        String publisher,
        String thumbnail,
        String isbn,
        Integer price
) {
}
