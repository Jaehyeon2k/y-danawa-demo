package yju.danawa.com.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yju.danawa.com.service.LibraryGrpcClient;

import java.util.List;

@RestController
@RequestMapping("/api/grpc/books")
public class GrpcBookController {

    private final LibraryGrpcClient libraryGrpcClient;

    public GrpcBookController(LibraryGrpcClient libraryGrpcClient) {
        this.libraryGrpcClient = libraryGrpcClient;
    }

    @GetMapping({"/search", "/search/", "/grpc-search", "/grpc-search/"})
    public List<GrpcBookResponse> searchBooks(
            @RequestParam("query") String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);

        return libraryGrpcClient.searchBooks(query, safePage, safeSize).stream()
                .map(item -> new GrpcBookResponse(
                        item.title(),
                        item.author(),
                        item.isbn(),
                        item.price(),
                        item.kakaoThumbnailUrl(),
                        item.imageUrl(),
                        item.imageProxyUrl(),
                        item.holdingChecked(),
                        item.holdingFound(),
                        item.holdingAvailable(),
                        item.holdingLocation(),
                        item.holdingCallNumber(),
                        item.holdingDetailUrl(),
                        item.holdingStatusText(),
                        item.holdingVerificationSource(),
                        item.holdingCheckedAt()))
                .toList();
    }

    @GetMapping({"/info", "/info/", "/book-info", "/book-info/"})
    public GrpcBookInfoResponse bookInfo(@RequestParam("isbn13") String isbn13) {
        String cleanIsbn = isbn13.replaceAll("[^0-9]", "");
        LibraryGrpcClient.BookInfoResult result = libraryGrpcClient.getBookInfo(cleanIsbn);
        return new GrpcBookInfoResponse(
                result.isbn13(),
                result.availabilityStatus(),
                result.imageUrl(),
                result.imageSource(),
                result.errorMessage(),
                result.checkedAt());
    }

    public record GrpcBookResponse(
            String title,
            String author,
            String isbn,
            int price,
            String kakaoThumbnailUrl,
            String imageUrl,
            String imageProxyUrl,
            boolean holdingChecked,
            boolean holdingFound,
            boolean holdingAvailable,
            String holdingLocation,
            String holdingCallNumber,
            String holdingDetailUrl,
            String holdingStatusText,
            String holdingVerificationSource,
            String holdingCheckedAt) {
    }

    public record GrpcBookInfoResponse(
            String isbn13,
            String availabilityStatus,
            String imageUrl,
            String imageSource,
            String errorMessage,
            String checkedAt) {
    }
}
