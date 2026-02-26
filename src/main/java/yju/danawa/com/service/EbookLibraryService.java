package yju.danawa.com.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.time.Duration;

@Service
public class EbookLibraryService {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    private final LibraryGrpcClient libraryGrpcClient;
    private final Cache<String, EbookStatus> cache;

    public EbookLibraryService(
            LibraryGrpcClient libraryGrpcClient,
            @Value("${app.ebook.cache-minutes:15}") int cacheMinutes
    ) {
        this.libraryGrpcClient = libraryGrpcClient;
        this.cache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, cacheMinutes)))
                .build();
    }

    public EbookStatus fetchByTitle(String title, String author, String publisher) {
        String normalizedTitle = normalizeTitle(title);
        String normalizedAuthor = normalizeText(author);
        String normalizedPublisher = normalizeText(publisher);
        if (normalizedTitle.isBlank()) {
            return EbookStatus.empty();
        }

        String cacheKey = normalizedTitle + "|" + normalizedAuthor + "|" + normalizedPublisher;
        EbookStatus cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            LibraryGrpcClient.EbookStatusResult result = libraryGrpcClient.getEbookStatus(
                    normalizedTitle,
                    normalizedAuthor,
                    normalizedPublisher
            );

            EbookStatus status = new EbookStatus(
                    result.title(),
                    result.found(),
                    result.totalHoldings(),
                    result.availableHoldings(),
                    safeDeepLink(result.deepLinkUrl(), normalizedTitle),
                    toDisplayStatus(result.statusText(), result.totalHoldings()),
                    result.errorMessage(),
                    result.checkedAt()
            );
            if (shouldCache(status)) {
                cache.put(cacheKey, status);
            }
            return status;
        } catch (Exception e) {
            return new EbookStatus(
                    normalizedTitle,
                    false,
                    0,
                    0,
                    safeDeepLink("", normalizedTitle),
                    "\uBBF8\uC18C\uC7A5",
                    "ebook_grpc_error",
                    ""
            );
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeTitle(String value) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            return "";
        }
        String stripped = normalized.replaceAll("\\s*(?:-|=|:|\\(|\\[).*$", "").trim();
        return stripped.isBlank() ? normalized : stripped;
    }

    private String safeDeepLink(String raw, String title) {
        if (raw != null && !raw.isBlank() && (raw.startsWith("https://ebook.yjc.ac.kr") || raw.startsWith("http://ebook.yjc.ac.kr"))) {
            return raw;
        }
        return "https://ebook.yjc.ac.kr/search/?srch_order=total&src_key=" + buildHybridSrcKey(title);
    }

    private boolean shouldCache(EbookStatus status) {
        if (status == null) {
            return false;
        }
        String err = status.errorMessage();
        return err == null || err.isBlank();
    }

    private String toDisplayStatus(String rawStatus, int totalHoldings) {
        String normalized = rawStatus == null ? "" : rawStatus.trim();
        if (!normalized.isBlank()) {
            return normalized;
        }
        return totalHoldings > 0 ? "\uC18C\uC7A5" : "\uBBF8\uC18C\uC7A5";
    }

    private String buildHybridSrcKey(String title) {
        String source = normalizeText(title);
        if (source.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char ch : source.toCharArray()) {
            if (ch == ' ') {
                out.append('+');
                continue;
            }
            if (ch < 128 && Character.isLetterOrDigit(ch)) {
                out.append(ch);
                continue;
            }
            Charset charset = isHangul(ch) ? EUC_KR : Charset.forName("UTF-8");
            byte[] bytes = String.valueOf(ch).getBytes(charset);
            for (byte b : bytes) {
                out.append('%');
                out.append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    private boolean isHangul(char ch) {
        return (ch >= '\u3131' && ch <= '\u318E') || (ch >= '\uAC00' && ch <= '\uD7A3');
    }

    public record EbookStatus(
            String title,
            boolean found,
            int totalHoldings,
            int availableHoldings,
            String deepLinkUrl,
            String statusText,
            String errorMessage,
            String checkedAt
    ) {
        public static EbookStatus empty() {
            return new EbookStatus("", false, 0, 0, "https://ebook.yjc.ac.kr/", "\uBBF8\uC18C\uC7A5", "title_required", "");
        }
    }
}
