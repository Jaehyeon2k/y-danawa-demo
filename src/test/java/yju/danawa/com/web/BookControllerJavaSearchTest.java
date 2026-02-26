package yju.danawa.com.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import yju.danawa.com.dto.BookDto;
import yju.danawa.com.service.BookImageSearchService;
import yju.danawa.com.service.BookPriceService;
import yju.danawa.com.service.BookService;
import yju.danawa.com.service.EbookLibraryService;
import yju.danawa.com.service.LibraryGrpcClient;
import yju.danawa.com.service.LibraryRateLimiter;
import yju.danawa.com.service.LibraryStatusMapper;
import yju.danawa.com.service.YjuLibraryService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookControllerJavaSearchTest {

    @Test
    void javaKeywordSearch_dedupesByIsbn13() {
        BookService bookService = mock(BookService.class);
        YjuLibraryService yjuLibraryService = mock(YjuLibraryService.class);
        BookPriceService bookPriceService = mock(BookPriceService.class);
        BookImageSearchService bookImageSearchService = mock(BookImageSearchService.class);
        LibraryGrpcClient libraryGrpcClient = mock(LibraryGrpcClient.class);
        EbookLibraryService ebookLibraryService = mock(EbookLibraryService.class);
        LibraryStatusMapper libraryStatusMapper = mock(LibraryStatusMapper.class);
        LibraryRateLimiter libraryRateLimiter = mock(LibraryRateLimiter.class);

        when(bookService.search("자바")).thenReturn(List.of(
                new BookDto("9788960777330", "자바의 정석", "남궁성", "도우출판", "/images/a.png", LocalDate.of(2019, 11, 29), 30000.0),
                new BookDto("9788960777330", "자바의 정석", "남궁성", "도우출판", "/images/b.png", LocalDate.of(2019, 11, 29), 30000.0)
        ));

        BookController controller = new BookController(
                bookService,
                yjuLibraryService,
                bookPriceService,
                bookImageSearchService,
                ebookLibraryService,
                libraryGrpcClient,
                libraryStatusMapper,
                libraryRateLimiter
        );

        List<BookController.SearchListItemResponse> response = controller.searchBooks("자바");
        assertFalse(response.isEmpty());
        assertEquals(1, response.size());
        assertEquals("9788960777330", response.get(0).isbn13());
    }

    @Test
    void bookDetail_usesLibraryStatusMapperAndReturnsAvailable() {
        BookService bookService = mock(BookService.class);
        YjuLibraryService yjuLibraryService = mock(YjuLibraryService.class);
        BookPriceService bookPriceService = mock(BookPriceService.class);
        BookImageSearchService bookImageSearchService = mock(BookImageSearchService.class);
        LibraryGrpcClient libraryGrpcClient = mock(LibraryGrpcClient.class);
        EbookLibraryService ebookLibraryService = mock(EbookLibraryService.class);
        LibraryStatusMapper libraryStatusMapper = mock(LibraryStatusMapper.class);
        LibraryRateLimiter libraryRateLimiter = mock(LibraryRateLimiter.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        String isbn13 = "9788960777330";
        BookDto dto = new BookDto(isbn13, "자바의 정석", "남궁성", "도우출판", "/images/a.png", LocalDate.of(2019, 11, 29), 30000.0);
        YjuLibraryService.LibraryAvailability availability = new YjuLibraryService.LibraryAvailability(
                true, true, "중앙도서관", "005.13", "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/9788960777330", null, "AVAILABLE"
        );

        when(request.getRemoteAddr()).thenReturn("127.0.0.10");
        when(libraryRateLimiter.allow("127.0.0.10:book-detail:" + isbn13)).thenReturn(true);
        when(bookService.findByIsbn13(isbn13)).thenReturn(Optional.of(dto));
        when(yjuLibraryService.checkAvailability(isbn13, null)).thenReturn(availability);
        when(ebookLibraryService.fetchByTitle("자바의 정석", "남궁성", "도우출판"))
                .thenReturn(new EbookLibraryService.EbookStatus(
                        "자바의 정석", false, 0, 0,
                        "https://ebook.yjc.ac.kr/search?query=9788960777330",
                        "미보유", "", ""
                ));
        when(libraryStatusMapper.normalize(true, true, "AVAILABLE", null))
                .thenReturn(new LibraryStatusMapper.NormalizedStatus(true, true, "AVAILABLE", "대출 가능"));

        BookController controller = new BookController(
                bookService,
                yjuLibraryService,
                bookPriceService,
                bookImageSearchService,
                ebookLibraryService,
                libraryGrpcClient,
                libraryStatusMapper,
                libraryRateLimiter
        );

        BookController.BookDetailResponse response = controller.getBookDetail(isbn13, request);
        assertEquals("AVAILABLE", response.library().status());
        assertEquals("대출 가능", response.library().statusText());
        assertTrue(response.vendors().aladin().contains(isbn13));
    }
}
