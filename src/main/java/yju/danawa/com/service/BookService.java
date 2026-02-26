package yju.danawa.com.service;

import yju.danawa.com.domain.Book;
import yju.danawa.com.dto.BookDto;
import yju.danawa.com.dto.InfiniteScrollResponse;
import yju.danawa.com.repository.BookImageRepository;
import yju.danawa.com.repository.BookRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookImageRepository bookImageRepository;

    public BookService(BookRepository bookRepository, BookImageRepository bookImageRepository) {
        this.bookRepository = bookRepository;
        this.bookImageRepository = bookImageRepository;
    }

    @Cacheable(cacheNames = "books", key = "#keyword")
    public List<BookDto> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = keyword.trim();
        List<Book> books = bookRepository.searchByKeyword(normalized);
        Map<String, String> imageUrlByIsbn = loadImageUrlByIsbn(books);
        return books.stream()
                .map(book -> new BookDto(
                        book.getIsbn(),
                        book.getTitle(),
                        book.getAuthor(),
                        book.getPublisher(),
                        resolveImageUrl(book, imageUrlByIsbn),
                        book.getPublishedDate(),
                        book.getPrice()))
                .collect(Collectors.toList());
    }

    public Optional<BookDto> findByIsbn13(String rawIsbn) {
        String isbn13 = normalizeIsbn13(rawIsbn);
        if (isbn13 == null) {
            return Optional.empty();
        }

        return bookRepository.findByIsbn(isbn13).map(book -> {
            Map<String, String> imageByIsbn = loadImageUrlByIsbn(List.of(book));
            return new BookDto(
                    book.getIsbn(),
                    book.getTitle(),
                    book.getAuthor(),
                    book.getPublisher(),
                    resolveImageUrl(book, imageByIsbn),
                    book.getPublishedDate(),
                    book.getPrice()
            );
        });
    }

    /**
     * Cursor-based Pagination for Infinite Scroll
     * ISBN을 커서로 사용 (Book의 PK가 ISBN이므로)
     * 
     * @param cursor 마지막으로 조회한 도서 ISBN (null이면 첫 페이지)
     * @param limit  한 번에 가져올 개수
     * @return InfiniteScrollResponse containing books and pagination info
     */
    public InfiniteScrollResponse<BookDto> getBooksWithCursor(String cursor, int limit) {
        List<Book> books;

        if (cursor == null || cursor.isBlank()) {
            // 첫 페이지: ISBN 기준 오름차순으로 limit개 조회
            books = bookRepository.findTopBooks(limit);
        } else {
            // 다음 페이지: cursor(ISBN)보다 큰 ISBN을 가진 도서 조회
            books = bookRepository.findBooksAfterCursor(cursor, limit);
        }

        Map<String, String> imageUrlByIsbn = loadImageUrlByIsbn(books);
        List<BookDto> bookDtos = books.stream()
                .map(book -> new BookDto(
                        book.getIsbn(),
                        book.getTitle(),
                        book.getAuthor(),
                        book.getPublisher(),
                        resolveImageUrl(book, imageUrlByIsbn),
                        book.getPublishedDate(),
                        book.getPrice()))
                .collect(Collectors.toList());

        return InfiniteScrollResponse.of(bookDtos, limit);
    }

    private String resolveImageUrl(Book book, Map<String, String> imageUrlByIsbn) {
        String url = null;
        if (book.getImageUrl() != null && !book.getImageUrl().isBlank()) {
            url = book.getImageUrl();
        } else {
            String isbn = book.getIsbn();
            if (isbn != null && !isbn.isBlank()) {
                url = imageUrlByIsbn.get(isbn);
            }
        }
        return normalizeStoredImageUrl(url);
    }

    private String normalizeIsbn13(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 13 && (digits.startsWith("978") || digits.startsWith("979"))) {
            return digits;
        }
        return null;
    }

    /**
     * Bare filenames (e.g. "893070599_l.jpg") stored in the DB are not
     * directly loadable by the browser. Convert them to a servable API path.
     */
    private String normalizeStoredImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        // Already a full URL or valid API/static path
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("/api/") || url.startsWith("/images/")) {
            return url;
        }
        // Bare filename → route through the file-based image endpoint
        if (!url.contains("/") && url.contains(".")) {
            return "/api/images/by-name/" + url;
        }
        return url;
    }

    private Map<String, String> loadImageUrlByIsbn(List<Book> books) {
        List<String> isbn13List = books.stream()
                .map(Book::getIsbn)
                .filter(isbn -> isbn != null && !isbn.isBlank())
                .distinct()
                .toList();
        if (isbn13List.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        List<BookImageRepository.BookImageByIsbnRow> rows = bookImageRepository.findFirstImageByIsbn13In(isbn13List);
        for (BookImageRepository.BookImageByIsbnRow row : rows) {
            if (row.getIsbn13() == null || row.getIsbn13().isBlank() || row.getId() == null) {
                continue;
            }
            result.put(row.getIsbn13(), "/api/images/" + row.getId());
        }
        return result;
    }
}
