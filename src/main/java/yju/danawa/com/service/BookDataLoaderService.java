package yju.danawa.com.service;

import yju.danawa.com.domain.Book;
import yju.danawa.com.dto.BookDto;
import yju.danawa.com.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookDataLoaderService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BookDataLoaderService.class);

    private final BookRepository bookRepository;
    private final ExternalBookService externalBookService;

    // 초기 로딩할 검색어 목록 (다양한 분야의 인기 도서)
    private static final String[] SEED_KEYWORDS = {
            // IT/프로그래밍
            "자바", "파이썬", "자바스크립트", "C언어", "리액트", "스프링",
            "데이터베이스", "알고리즘", "운영체제", "네트워크", "코딩",
            "머신러닝", "딥러닝", "인공지능", "클라우드", "AWS",
            // 문학/소설
            "인간실격", "데미안", "1984", "어린왕자", "해리포터", "노인과 바다",
            "위대한 개츠비", "호밀밭의 파수꾼", "총, 균, 쇠", "사피엔스",
            // 자기계발/비즈니스
            "습관의 힘", "생각하는 힘", "마케팅", "경영학", "부의 추월차선",
            "원칙", "데일 카네기", "타이탄의 도구들", "부자 아빠 가난한 아빠",
            // 과학/교양
            "미적분학", "물리학", "화학", "생물학", "경제학", "통계학",
            "심리학", "철학", "역사", "세계사", "한국사", "우주", "뇌과학"
    };

    public BookDataLoaderService(BookRepository bookRepository,
            ExternalBookService externalBookService) {
        this.bookRepository = bookRepository;
        this.externalBookService = externalBookService;
    }

    @Override
    public void run(String... args) {
        long existingCount = bookRepository.count();
        log.info("현재 DB에 저장된 도서 수: {}", existingCount);

        // DB가 비어있거나 50권 미만이면 외부 API에서 데이터 로드
        if (existingCount < 50) {
            log.info("DB에 도서 데이터가 부족합니다. 외부 API에서 실제 데이터를 가져옵니다...");
            loadBooksFromExternalApi();
        } else {
            log.info("충분한 도서 데이터가 있습니다 ({}권). 외부 API 호출을 건너뜁니다.", existingCount);
        }
    }

    private void loadBooksFromExternalApi() {
        List<Book> booksToSave = new ArrayList<>();
        int totalFetched = 0;

        for (String keyword : SEED_KEYWORDS) {
            try {
                log.info("'{}' 키워드로 도서 검색 중...", keyword);

                // 알라딘 API 우선 시도 (더 안정적)
                List<BookDto> books = externalBookService.search(keyword, "aladin");

                // 알라딘 실패 시 카카오 시도
                if (books.isEmpty()) {
                    books = externalBookService.search(keyword, "kakao");
                }

                for (BookDto dto : books) {
                    // ISBN이 있고 DB에 없는 책만 추가
                    if (dto.isbn() != null && !dto.isbn().isBlank()
                            && !bookRepository.existsById(dto.isbn())) {

                        // imageUrl이 null이거나 비어있으면 기본 이미지 사용하지 않고 그대로 저장
                        // imageUrl은 ExternalBookService에서 이미 normalizeImageUrl()로 처리되어 고화질로 변환됨
                        String imageUrl = dto.imageUrl();

                        Book book = new Book();
                        book.setIsbn(dto.isbn());
                        book.setTitle(dto.title());
                        book.setAuthor(dto.author());
                        book.setPublisher(dto.publisher());
                        book.setImageUrl(imageUrl);
                        book.setPrice(dto.price());

                        // publishedDate 처리
                        if (dto.publishedDate() != null) {
                            book.setPublishedDate(dto.publishedDate());
                        } else {
                            book.setPublishedDate(LocalDate.now());
                        }

                        booksToSave.add(book);
                        totalFetched++;

                        // 메모리 관리: 50권마다 배치 저장
                        if (booksToSave.size() >= 50) {
                            bookRepository.saveAll(booksToSave);
                            log.info("{}권 저장 완료 (누적: {}권)", booksToSave.size(), totalFetched);
                            booksToSave.clear();
                        }
                    }
                }

                // API 호출 간격 (429 Too Many Requests 방지)
                Thread.sleep(500);

            } catch (Exception e) {
                log.warn("'{}' 키워드 검색 실패: {}", keyword, e.getMessage());
            }

            // 충분한 데이터를 수집하면 중단 (200권 이상)
            if (totalFetched >= 200) {
                log.info("목표 도서 수({})에 도달했습니다.", totalFetched);
                break;
            }
        }

        // 남은 도서 저장
        if (!booksToSave.isEmpty()) {
            bookRepository.saveAll(booksToSave);
            log.info("최종 {}권 저장 완료", booksToSave.size());
        }

        log.info("총 {}권의 도서를 외부 API에서 가져와 저장했습니다.", totalFetched);
        log.info("현재 DB 총 도서 수: {}", bookRepository.count());
    }
}
