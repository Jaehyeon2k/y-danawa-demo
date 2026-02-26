package yju.danawa.com.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import yju.danawa.com.dto.BookPriceDto;

import jakarta.annotation.PreDestroy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BookPriceService {

    private static final Logger log = LoggerFactory.getLogger(BookPriceService.class);
    private static final int TIMEOUT_SECONDS = 10;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final WebClient webClient;

    public BookPriceService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 여러 온라인 서점의 가격 정보를 조회
     * 실제 웹 페이지에서 가격을 크롤링
     */
    public List<BookPriceDto> getPrices(String isbn, String title) {
        log.info("도서 가격 조회 시작: ISBN={}, title={}", isbn, title);

        List<BookPriceDto> prices = new ArrayList<>();

        // 검색 키워드: ISBN이 있으면 우선, 없으면 제목 사용
        String searchKey = (isbn != null && !isbn.isBlank()) ? isbn : title;

        if (searchKey == null || searchKey.isBlank()) {
            log.warn("ISBN과 제목이 모두 없어서 가격 조회 불가");
            return prices;
        }

        // 병렬로 각 서점에서 가격 조회
        List<CompletableFuture<BookPriceDto>> futures = new ArrayList<>();

        futures.add(CompletableFuture.supplyAsync(() -> fetchYes24Price(searchKey), executorService));
        futures.add(CompletableFuture.supplyAsync(() -> fetchAladinPrice(searchKey), executorService));
        futures.add(CompletableFuture.supplyAsync(() -> fetchKyoboPrice(searchKey), executorService));
        futures.add(CompletableFuture.supplyAsync(() -> fetchInterparkPrice(searchKey), executorService));

        // 모든 결과 수집 (최대 15초 대기)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(15, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                log.warn("일부 가격 조회 시간 초과: {}", ex.getMessage());
                return null;
            })
            .join();

        for (CompletableFuture<BookPriceDto> future : futures) {
            try {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    prices.add(future.get());
                }
            } catch (Exception e) {
                log.warn("가격 정보 수집 중 오류: {}", e.getMessage());
            }
        }

        log.info("가격 조회 완료: {} 개 서점", prices.size());
        return prices;
    }

    private BookPriceDto fetchYes24Price(String query) {
        String url = buildYes24Url(query);
        try {
            log.debug("YES24 가격 조회: {}", query);
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .get();

            // YES24: 첫 번째 상품의 가격 추출
            Element priceElement = doc.selectFirst("em.yes_m, strong.txt_num");
            if (priceElement != null) {
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    log.info("YES24 가격 발견: {}원", price);
                    return new BookPriceDto("yes24", "YES24", price, url, "무료배송", true);
                }
            }
        } catch (Exception e) {
            log.warn("YES24 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("yes24", "YES24", url);
    }

    private BookPriceDto fetchAladinPrice(String query) {
        String url = buildAladinUrl(query);
        try {
            log.debug("알라딘 가격 조회: {}", query);
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .get();

            // 알라딘: ss_p2 클래스의 가격 추출
            Element priceElement = doc.selectFirst("span.ss_p2 b, span.ss_p2");
            if (priceElement != null) {
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    log.info("알라딘 가격 발견: {}원", price);
                    return new BookPriceDto("aladin", "알라딘", price, url, "무료배송", true);
                }
            }
        } catch (Exception e) {
            log.warn("알라딘 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("aladin", "알라딘", url);
    }

    private BookPriceDto fetchKyoboPrice(String query) {
        String url = buildKyoboUrl(query);
        try {
            log.debug("교보문고 가격 조회: {}", query);
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .get();

            // 교보문고: 할인가 추출
            Element priceElement = doc.selectFirst("span.val, span.price_val");
            if (priceElement != null) {
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    log.info("교보문고 가격 발견: {}원", price);
                    return new BookPriceDto("kyobo", "교보문고", price, url, "무료배송", true);
                }
            }
        } catch (Exception e) {
            log.warn("교보문고 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("kyobo", "교보문고", url);
    }

    private BookPriceDto fetchInterparkPrice(String query) {
        String url = buildInterparkUrl(query);
        try {
            log.debug("인터파크 가격 조회: {}", query);
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .get();

            // 인터파크: 할인가 추출
            Element priceElement = doc.selectFirst("em.price_real, span.price");
            if (priceElement != null) {
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    log.info("인터파크 가격 발견: {}원", price);
                    return new BookPriceDto("interpark", "인터파크", price, url, "무료배송", true);
                }
            }
        } catch (Exception e) {
            log.warn("인터파크 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("interpark", "인터파크", url);
    }

    private String buildYes24Url(String query) {
        return "https://www.yes24.com/Product/Search?domain=BOOK&query=" + encodeQuery(query);
    }

    private String buildAladinUrl(String query) {
        return "https://www.aladin.co.kr/search/wsearchresult.aspx?SearchTarget=Book&SearchWord=" + encodeQuery(query);
    }

    private String buildKyoboUrl(String query) {
        return "https://search.kyobobook.co.kr/search?keyword=" + encodeQuery(query);
    }

    private String buildInterparkUrl(String query) {
        return "https://book.interpark.com/search?query=" + encodeQuery(query);
    }

    private static String encodeQuery(String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}

