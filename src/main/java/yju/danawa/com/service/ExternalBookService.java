package yju.danawa.com.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.HttpClient;
import yju.danawa.com.dto.BookDto;
import yju.danawa.com.service.dto.AladinItemSearchResponse;
import yju.danawa.com.service.dto.KakaoBookResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ExternalBookService {

    private static final Logger log = LoggerFactory.getLogger(ExternalBookService.class);
    private static final int ALADIN_MAX_RETRIES = 3;
    private static final long ALADIN_RETRY_BASE_MS = 400L;
    private static final int GOOGLE_MAX_RESULTS = 40;
    private static final String COMMON_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final String PLACEHOLDER_IMAGE_URL =
            "https://placehold.co/120x174?text=%EC%9D%B4%EB%AF%B8%EC%A7%80+%EC%97%86%EC%9D%8C";

    private final WebClient webClient;
    private final String kakaoRestApiKey;
    private final String googleApiKey;
    private final String aladinTtbKey;

    public ExternalBookService(
            WebClient.Builder builder,
            @Value("${app.external.kakao-rest-api-key:}") String kakaoRestApiKey,
            @Value("${app.external.google-api-key:${GOOGLE_API_KEY:}}") String googleApiKey,
            @Value("${app.external.aladin-ttb-key:}") String aladinTtbKey
    ) {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(10));
        this.webClient = builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
        this.kakaoRestApiKey = kakaoRestApiKey;
        this.googleApiKey = googleApiKey;
        this.aladinTtbKey = aladinTtbKey;
    }

    @Cacheable(cacheNames = "externalBooks", key = "#query + '::' + #source")
    public List<BookDto> search(String query, String source) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // ISBN cleaning: if the query looks like an ISBN with hyphens, strip to pure digits.
        String isbnCandidate = query.trim().replaceAll("[^0-9]", "");
        if (isbnCandidate.length() == 10 || isbnCandidate.length() == 13) {
            query = isbnCandidate;
        }

        String normalizedSource = Optional.ofNullable(source).orElse("auto").toLowerCase(Locale.ROOT);
        return switch (normalizedSource) {
            case "aladin" -> searchAladin(query);
            case "google" -> searchGoogle(query);
            case "kakao" -> searchKakao(query);
            case "auto" -> {
                // Aladin first (highest quality ISBN cover), then Kakao, then Google.
                List<BookDto> aladinResult = safeSearchAladin(query);
                List<BookDto> kakaoResult = safeSearchKakao(query);
                List<BookDto> googleResult = safeSearchGoogle(query);
                yield mergeByPriority(aladinResult, kakaoResult, googleResult);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 소스입니다: " + source);
        };
    }

    private List<BookDto> safeSearchKakao(String query) {
        try {
            return searchKakao(query);
        } catch (Exception ex) {
            log.warn("카카오 폴백 실패: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<BookDto> safeSearchGoogle(String query) {
        try {
            return searchGoogle(query);
        } catch (Exception ex) {
            log.warn("구글 폴백 실패: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<BookDto> safeSearchAladin(String query) {
        try {
            return searchAladin(query);
        } catch (Exception ex) {
            log.warn("알라딘 폴백 실패: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<BookDto> searchKakao(String query) {
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            log.warn("카카오 API 키가 설정되지 않았습니다.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "카카오 API 키가 설정되지 않았습니다.");
        }

        try {
            log.info("카카오 API 호출: query={}", query);
            KakaoBookResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("dapi.kakao.com")
                            .path("/v3/search/book")
                            .queryParam("query", query)
                            .queryParam("size", 50)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .header("User-Agent", COMMON_UA)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("카카오 API 오류: status={}", clientResponse.statusCode());
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "카카오 API 호출 실패: " + clientResponse.statusCode()
                                );
                            }
                    )
                    .bodyToMono(KakaoBookResponse.class)
                    .block();

            if (response == null || response.documents() == null) {
                log.warn("카카오 API 응답이 비어 있습니다.");
                return Collections.emptyList();
            }

            log.info("카카오 API 성공: {}건", response.documents().size());
            return response.documents().stream()
                    .map(doc -> {
                        String isbn = extractIsbn(doc.isbn());
                        String imageUrl = coverOrFallback(normalizeImageUrl(doc.thumbnail()), isbn);
                        return new BookDto(
                                isbn,
                                sanitizeText(doc.title()),
                                sanitizeText(String.join(", ", doc.authors())),
                                sanitizeText(doc.publisher()),
                                imageUrl,
                                null,
                                sanitizePrice(doc.price() == null ? null : doc.price().doubleValue())
                        );
                    })
                    .collect(Collectors.toList());
        } catch (ResponseStatusException e) {
            log.error("카카오 검색 실패: {}", e.getReason());
            throw e;
        } catch (Exception e) {
            log.error("카카오 검색 예외: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 검색 실패: " + e.getMessage());
        }
    }

    private List<BookDto> searchGoogle(String query) {
        try {
            log.info("구글 도서 API 호출: query={}", query);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("www.googleapis.com")
                                .path("/books/v1/volumes")
                                .queryParam("q", query)
                                .queryParam("maxResults", GOOGLE_MAX_RESULTS)
                                .queryParam("printType", "books")
                                .queryParam("langRestrict", "ko");
                        if (googleApiKey != null && !googleApiKey.isBlank()) {
                            builder.queryParam("key", googleApiKey);
                        }
                        return builder.build();
                    })
                    .header("User-Agent", COMMON_UA)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (root == null) {
                return Collections.emptyList();
            }

            Object itemsObj = root.get("items");
            if (!(itemsObj instanceof List<?> items)) {
                return Collections.emptyList();
            }

            List<BookDto> results = new ArrayList<>();
            for (Object itemObj : items) {
                if (!(itemObj instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                Map<String, Object> volumeInfo = map(itemMap.get("volumeInfo"));
                Map<String, Object> saleInfo = map(itemMap.get("saleInfo"));

                String title = text(volumeInfo.get("title"));
                if (title.isBlank()) {
                    continue;
                }

                String author = joinArray(volumeInfo.get("authors"));
                String publisher = text(volumeInfo.get("publisher"));
                String isbn = extractGoogleIsbn(volumeInfo.get("industryIdentifiers"));
                String imageUrl = extractGoogleImage(volumeInfo.get("imageLinks"));
                Double price = extractGooglePrice(map(saleInfo.get("listPrice")));

                results.add(new BookDto(
                        isbn,
                        title,
                        author,
                        publisher,
                        coverOrFallback(normalizeImageUrl(imageUrl), isbn),
                        null,
                        price
                ));
            }

            log.info("구글 도서 API 성공: {}건", results.size());
            return results;
        } catch (WebClientResponseException e) {
            log.error("구글 API 오류: {}", e.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 API 호출 실패: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("구글 검색 예외: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "구글 검색 실패: " + e.getMessage());
        }
    }

    private List<BookDto> searchAladin(String query) {
        if (aladinTtbKey == null || aladinTtbKey.isBlank()) {
            log.warn("알라딘 API 키가 설정되지 않았습니다.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "알라딘 API 키가 설정되지 않았습니다.");
        }

        try {
            log.info("알라딘 API 호출: query={}", query);
            String normalizedQuery = query.replaceAll("[^0-9]", "");

            final String queryType;
            final String searchQuery;

            if (normalizedQuery.length() == 10) {
                String isbn13 = convertIsbn10ToIsbn13(normalizedQuery);
                if (isbn13 != null) {
                    queryType = "ISBN13";
                    searchQuery = isbn13;
                } else {
                    queryType = "Title";
                    searchQuery = query;
                }
            } else if (normalizedQuery.length() == 13) {
                queryType = "ISBN13";
                searchQuery = normalizedQuery;
            } else {
                queryType = "Title";
                searchQuery = query;
            }

            AladinItemSearchResponse response = callAladinWithRetry(searchQuery, queryType);

            if (response == null || response.item() == null) {
                log.warn("알라딘 API 응답이 비어 있습니다.");
                return Collections.emptyList();
            }

            log.info("알라딘 API 성공: {}건", response.item().size());

            return response.item().stream()
                    .map(item -> {
                        String isbn = item.isbn13() != null && !item.isbn13().isBlank() ? item.isbn13() : item.isbn();
                        String imageUrl = coverOrFallback(normalizeImageUrl(item.cover()), isbn);
                        return new BookDto(
                                isbn,
                                sanitizeText(item.title()),
                                sanitizeText(item.author()),
                                sanitizeText(item.publisher()),
                                imageUrl,
                                null,
                                sanitizePrice(item.priceSales() == null ? null : item.priceSales().doubleValue())
                        );
                    })
                    .collect(Collectors.toList());
        } catch (ResponseStatusException e) {
            log.error("알라딘 검색 실패: {}", e.getReason());
            throw e;
        } catch (Exception e) {
            log.error("알라딘 검색 예외: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "알라딘 검색 실패: " + e.getMessage());
        }
    }

    private AladinItemSearchResponse callAladinWithRetry(String searchQuery, String queryType) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= ALADIN_MAX_RETRIES; attempt++) {
            try {
                return webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("http")
                                .host("www.aladin.co.kr")
                                .path("/ttb/api/ItemSearch.aspx")
                                .queryParam("ttbkey", aladinTtbKey)
                                .queryParam("Query", searchQuery)
                                .queryParam("QueryType", queryType)
                                .queryParam("SearchTarget", "Book")
                                .queryParam("MaxResults", 50)
                                .queryParam("start", 1)
                                .queryParam("Cover", "Big")
                                .queryParam("output", "js")
                                .queryParam("Version", "20131101")
                                .build())
                        .header("User-Agent", COMMON_UA)
                        .header("Accept", "application/json,text/plain,*/*")
                        .header("Referer", "https://www.aladin.co.kr/")
                        .retrieve()
                        .bodyToMono(AladinItemSearchResponse.class)
                        .block();
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                lastError = new RuntimeException("status=" + status, e);

                if (!isRetryableAladinStatus(status) || attempt == ALADIN_MAX_RETRIES) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "알라딘 API 호출 실패: " + status
                    );
                }

                long delay = ALADIN_RETRY_BASE_MS * attempt;
                log.warn("알라딘 API 재시도 {}/{} (status: {}, {}ms 후)",
                        attempt, ALADIN_MAX_RETRIES, status, delay);
                sleepSilently(delay);
            } catch (Exception e) {
                lastError = new RuntimeException(e);
                if (attempt == ALADIN_MAX_RETRIES) {
                    break;
                }
                long delay = ALADIN_RETRY_BASE_MS * attempt;
                log.warn("알라딘 API 재시도 {}/{} (예외: {}, {}ms 후)",
                        attempt, ALADIN_MAX_RETRIES, e.getClass().getSimpleName(), delay);
                sleepSilently(delay);
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "알라딘 API 재시도 후에도 실패: " + (lastError == null ? "원인 불명" : lastError.getMessage())
        );
    }

    private boolean isRetryableAladinStatus(int status) {
        return status == 403 || status == 408 || status == 429 || status >= 500;
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private List<BookDto> mergeByPriority(List<BookDto> primary, List<BookDto> secondary, List<BookDto> tertiary) {
        Map<String, BookDto> merged = new LinkedHashMap<>();
        for (BookDto item : primary) {
            merged.put(bookKey(item), item);
        }
        applyPriorityFill(merged, secondary);
        applyPriorityFill(merged, tertiary);
        return new ArrayList<>(merged.values());
    }

    private void applyPriorityFill(Map<String, BookDto> merged, List<BookDto> candidates) {
        for (BookDto candidate : candidates) {
            String key = bookKey(candidate);
            BookDto existing = merged.get(key);
            if (existing == null) {
                merged.put(key, candidate);
                continue;
            }
            if (!hasImage(existing) && hasImage(candidate)) {
                merged.put(key, new BookDto(
                        existing.isbn(),
                        existing.title(),
                        existing.author(),
                        existing.publisher(),
                        candidate.imageUrl(),
                        existing.publishedDate(),
                        existing.price() != null ? existing.price() : candidate.price()
                ));
            }
        }
    }

    private String convertIsbn10ToIsbn13(String isbn10) {
        if (isbn10 == null || isbn10.length() != 10) {
            return null;
        }

        try {
            String base = "978" + isbn10.substring(0, 9);
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                int digit = Character.getNumericValue(base.charAt(i));
                sum += (i % 2 == 0) ? digit : digit * 3;
            }

            int checkDigit = (10 - (sum % 10)) % 10;
            return base + checkDigit;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractIsbn(String raw) {
        if (raw == null) {
            return "";
        }
        // Kakao may return "ISBN10 ISBN13" (e.g., "8966262600 9788966262601").
        // Prefer the ISBN-13 token (13 digits starting with 978/979).
        String[] parts = raw.split("\\s+");
        for (String part : parts) {
            String digits = part.replaceAll("[^0-9]", "");
            if (digits.length() == 13 && (digits.startsWith("978") || digits.startsWith("979"))) {
                return digits;
            }
        }
        // Fallback: return the first non-empty token's digits.
        String first = parts.length > 0 ? parts[0] : raw;
        return first.replaceAll("[^0-9]", "");
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String normalized = imageUrl;

        if (normalized.startsWith("http://")) {
            normalized = "https://" + normalized.substring(7);
        }

        if (normalized.contains("search1.kakaocdn.net") || normalized.contains("img1.kakaocdn.net")) {
            normalized = normalized.replaceAll("R480x696", "R120x174");
        }

        if (normalized.contains("image.aladin.co.kr")) {
            normalized = normalized.replace("/cover1/", "/cover500/");
            normalized = normalized.replace("/coversum/", "/cover500/");
        }

        return normalized;
    }

    private String bookKey(BookDto book) {
        String isbn = normalize(book.isbn());
        if (!isbn.isBlank()) {
            // Normalize ISBN-10 → ISBN-13 so the same book always gets the same key.
            String digits = isbn.replaceAll("[^0-9]", "");
            if (digits.length() == 10) {
                String isbn13 = convertIsbn10ToIsbn13(digits);
                if (isbn13 != null) return "isbn:" + isbn13;
            }
            return "isbn:" + isbn;
        }
        return "meta:" + normalize(book.title()) + "|" + normalize(book.author());
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean hasImage(BookDto book) {
        return book.imageUrl() != null && !book.imageUrl().isBlank();
    }

    private String coverOrFallback(String imageUrl, String isbnRaw) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }
        // Return a guaranteed-reachable placeholder so the browser never gets a 404.
        return PLACEHOLDER_IMAGE_URL;
    }

    private String extractGoogleIsbn(Object idsObj) {
        if (!(idsObj instanceof List<?> ids)) return "";
        String isbn10 = "";
        for (Object idObj : ids) {
            if (!(idObj instanceof Map<?, ?> idMapRaw)) {
                continue;
            }
            Map<String, Object> idMap = map(idMapRaw);
            String type = text(idMap.get("type"));
            String identifier = text(idMap.get("identifier"));
            if ("ISBN_13".equalsIgnoreCase(type) && !identifier.isBlank()) {
                return identifier;
            }
            if ("ISBN_10".equalsIgnoreCase(type) && !identifier.isBlank()) {
                isbn10 = identifier;
            }
        }
        return isbn10;
    }

    private String extractGoogleImage(Object imageLinksObj) {
        Map<String, Object> imageLinks = map(imageLinksObj);
        if (imageLinks.isEmpty()) return null;
        String preferred = text(imageLinks.get("thumbnail"));
        if (preferred.isBlank()) {
            preferred = text(imageLinks.get("smallThumbnail"));
        }
        return preferred;
    }

    private Double extractGooglePrice(Map<String, Object> listPrice) {
        if (listPrice.isEmpty()) return null;
        Object amount = listPrice.get("amount");
        if (amount instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private String joinArray(Object arrayObj) {
        if (!(arrayObj instanceof List<?> array)) return "";
        List<String> values = new ArrayList<>();
        for (Object node : array) {
            String v = text(node);
            if (!v.isBlank()) values.add(v);
        }
        return String.join(", ", values);
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Collections.emptyMap();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Sanitize author/publisher/title text.
     * Strips lone "/" or "-", trims whitespace, returns null for empty/invalid values.
     */
    private String sanitizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        // Remove leading/trailing slashes
        trimmed = trimmed.replaceAll("^/+|/+$", "").trim();
        if (trimmed.isEmpty() || trimmed.equals("/") || trimmed.equals("-")) {
            return null;
        }
        return trimmed;
    }

    /**
     * Sanitize price: return null if zero or negative (hides "0원" in UI).
     */
    private Double sanitizePrice(Double price) {
        if (price == null || price <= 0) return null;
        return price;
    }
}
