package yju.danawa.com.service;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import yju.danawa.com.grpc.BookRequest;
import yju.danawa.com.grpc.BookResponse;
import yju.danawa.com.grpc.BookInfoRequest;
import yju.danawa.com.grpc.BookInfoResponse;
import yju.danawa.com.grpc.EbookRequest;
import yju.danawa.com.grpc.EbookResponse;
import yju.danawa.com.grpc.HealthCheckRequest;
import yju.danawa.com.grpc.HealthCheckResponse;
import yju.danawa.com.grpc.LibraryRequest;
import yju.danawa.com.grpc.LibraryResponse;
import yju.danawa.com.grpc.LibraryServiceGrpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class LibraryGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(LibraryGrpcClient.class);
    private static final int GRPC_DEADLINE_SECONDS = 30;
    private static final int EBOOK_GRPC_DEADLINE_SECONDS = 20;

    @Value("${app.grpc.host:ydanawa-library-scraper}")
    private String grpcHost;

    @Value("${app.grpc.port:9090}")
    private int grpcPort;

    private ManagedChannel channel;
    private LibraryServiceGrpc.LibraryServiceBlockingStub blockingStub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder
                .forAddress(grpcHost, grpcPort)
                .usePlaintext()
                .build();

        blockingStub = LibraryServiceGrpc.newBlockingStub(channel);
        log.info("gRPC channel initialized: {}:{}", grpcHost, grpcPort);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    public LibraryResponse checkLibrary(String isbn, String title, String author) {
        try {
            LibraryRequest.Builder requestBuilder = LibraryRequest.newBuilder();
            if (isbn != null && !isbn.isBlank()) {
                requestBuilder.setIsbn(isbn);
            }
            if (title != null && !title.isBlank()) {
                requestBuilder.setTitle(title);
            }
            if (author != null && !author.isBlank()) {
                requestBuilder.setAuthor(author);
            }
            return blockingStub.withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getLibraryStatus(requestBuilder.build());
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("gRPC call failed: getLibraryStatus", e);
        }
    }

    public HealthCheckResponse healthCheck() {
        try {
            HealthCheckRequest request = HealthCheckRequest.newBuilder().build();
            return blockingStub.withDeadlineAfter(10, TimeUnit.SECONDS)
                    .healthCheck(request);
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("gRPC call failed: healthCheck", e);
        }
    }

    public List<BookResult> searchBooks(String query, int page, int size) {
        try {
            BookRequest request = BookRequest.newBuilder()
                    .setQuery(query == null ? "" : query)
                    .setPage(page)
                    .setSize(size)
                    .build();

            BookResponse response = blockingStub.withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .searchBooks(request);
            return response.getBooksList().stream()
                    .map(item -> new BookResult(
                            item.getTitle(),
                            item.getAuthor(),
                            item.getIsbn(),
                            item.getPrice(),
                            item.getKakaoThumbnailUrl(),
                            item.getImageUrl(),
                            item.getImageProxyUrl(),
                            item.getHolding().getChecked(),
                            item.getHolding().getFound(),
                            item.getHolding().getAvailable(),
                            item.getHolding().getLocation(),
                            item.getHolding().getCallNumber(),
                            item.getHolding().getDetailUrl(),
                            item.getHolding().getStatusText(),
                            item.getHolding().getVerificationSource(),
                            item.getHolding().getCheckedAt()))
                    .toList();
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("gRPC call failed: searchBooks", e);
        }
    }

    public BookInfoResult getBookInfo(String isbn13) {
        try {
            BookInfoRequest request = BookInfoRequest.newBuilder()
                    .setIsbn13(isbn13 == null ? "" : isbn13)
                    .build();
            BookInfoResponse response = blockingStub.withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getBookInfo(request);
            return new BookInfoResult(
                    response.getIsbn13(),
                    response.getAvailabilityStatus(),
                    response.getImageUrl(),
                    response.getImageSource(),
                    response.getErrorMessage(),
                    response.getCheckedAt());
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("gRPC call failed: getBookInfo", e);
        }
    }

    public EbookStatusResult getEbookStatus(String title, String author, String publisher) {
        try {
            EbookRequest request = EbookRequest.newBuilder()
                    .setTitle(title == null ? "" : title)
                    .setAuthor(author == null ? "" : author)
                    .setPublisher(publisher == null ? "" : publisher)
                    .build();
            EbookResponse response = blockingStub.withDeadlineAfter(EBOOK_GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getEbookStatus(request);
            return new EbookStatusResult(
                    response.getTitle(),
                    response.getFound(),
                    response.getTotalHoldings(),
                    response.getAvailableHoldings(),
                    response.getDeepLinkUrl(),
                    response.getStatusText(),
                    response.getErrorMessage(),
                    response.getCheckedAt()
            );
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("gRPC call failed: getEbookStatus", e);
        }
    }

    public record BookResult(
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

    public record BookInfoResult(
            String isbn13,
            String availabilityStatus,
            String imageUrl,
            String imageSource,
            String errorMessage,
            String checkedAt) {
    }

    public record EbookStatusResult(
            String title,
            boolean found,
            int totalHoldings,
            int availableHoldings,
            String deepLinkUrl,
            String statusText,
            String errorMessage,
            String checkedAt
    ) {
    }
}
