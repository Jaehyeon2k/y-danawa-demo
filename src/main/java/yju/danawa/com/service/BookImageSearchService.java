package yju.danawa.com.service;

import yju.danawa.com.domain.BookRecord;
import yju.danawa.com.dto.BookSearchItemDto;
import yju.danawa.com.repository.BookImageRepository;
import yju.danawa.com.repository.BookRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BookImageSearchService {

    private final BookRecordRepository bookRecordRepository;
    private final BookImageRepository bookImageRepository;
    private final String placeholderImageUrl;

    public BookImageSearchService(
            BookRecordRepository bookRecordRepository,
            BookImageRepository bookImageRepository,
            @Value("${app.placeholder-image-url:https://placehold.co/120x174?text=%EC%9D%B4%EB%AF%B8%EC%A7%80+%EC%97%86%EC%9D%8C}") String placeholderImageUrl
    ) {
        this.bookRecordRepository = bookRecordRepository;
        this.bookImageRepository = bookImageRepository;
        this.placeholderImageUrl = placeholderImageUrl;
    }

    public Page<BookSearchItemDto> search(String query, Pageable pageable) {
        String normalized = normalizeQuery(query);
        if (normalized == null || normalized.isBlank()) {
            return Page.empty(pageable);
        }

        Page<BookRecord> page = bookRecordRepository.searchByTitleNorm(normalized, pageable);
        List<Long> bookIds = page.getContent().stream().map(BookRecord::getId).toList();
        Map<Long, List<String>> imageUrlsByBook = loadImageUrls(bookIds);

        return page.map(book -> new BookSearchItemDto(
                book.getId(),
                book.getTitle(),
                book.getTitleNorm(),
                book.getIsbn13(),
                book.getAuthors(),
                book.getPublisher(),
                book.getPublishedAt(),
                imageUrlsByBook.getOrDefault(book.getId(), List.of())));
    }

    private Map<Long, List<String>> loadImageUrls(List<Long> bookIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (bookIds.isEmpty()) {
            return result;
        }

        List<BookImageRepository.BookImageIdRow> rows = bookImageRepository.findImageIdsByBookIds(bookIds);
        for (BookImageRepository.BookImageIdRow row : rows) {
            result.computeIfAbsent(row.getBookId(), key -> new ArrayList<>())
                    .add("/api/images/" + row.getId());
        }

        List<Long> missingBookIds = bookIds.stream()
                .filter(bookId -> !result.containsKey(bookId))
                .toList();
        if (missingBookIds.isEmpty()) {
            return result;
        }

        List<BookRecordRepository.BookFileImageRow> fileRows = bookRecordRepository.findFileImageUrlsByBookIds(missingBookIds);
        for (BookRecordRepository.BookFileImageRow row : fileRows) {
            if (row.getBookId() == null || row.getImageUrl() == null || row.getImageUrl().isBlank()) {
                continue;
            }
            result.computeIfAbsent(row.getBookId(), key -> new ArrayList<>())
                    .add(normalizeStoredImageUrl(row.getImageUrl()));
        }

        // Guarantee a non-empty image URL for every book so frontend never requests missing paths.
        for (Long bookId : bookIds) {
            if (!result.containsKey(bookId) || result.get(bookId).isEmpty()) {
                result.put(bookId, List.of(placeholderImageUrl));
            }
        }
        return result;
    }

    /**
     * Bare filenames stored in the DB need to be converted to a servable path.
     */
    private String normalizeStoredImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("/api/") || url.startsWith("/images/")) {
            return url;
        }
        if (!url.contains("/") && url.contains(".")) {
            return "/api/images/by-name/" + url;
        }
        return url;
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }
}
