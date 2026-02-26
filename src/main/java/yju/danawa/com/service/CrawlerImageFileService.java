package yju.danawa.com.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
public class CrawlerImageFileService {

    private final Path imageDirectory;

    public CrawlerImageFileService(
            @Value("${app.images.directory:${CRAWLER_OUT:c:/yjudanawa-damo/com/image-crawler}}") String imageDirectory) {
        this.imageDirectory = Paths.get(imageDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void ensureImageDirectoryExists() {
        try {
            Files.createDirectories(imageDirectory);
        } catch (IOException ignored) {
            // If the directory cannot be created now, read operations will still fail safely.
        }
    }

    public Optional<ImageFileData> readImage(String fileName) {
        Path filePath = resolveImagePath(fileName);
        if (!Files.isRegularFile(filePath)) {
            return Optional.empty();
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] bytes = inputStream.readAllBytes();
            String contentType = Files.probeContentType(filePath);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }
            return Optional.of(new ImageFileData(bytes, contentType));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    public void deleteImageDirectoryContentsWithRetry(int maxAttempts) throws IOException {
        // Intentionally disabled to avoid EBUSY conflicts on Windows.
        // Keep method for backward compatibility with existing callers.
    }

    private Path resolveImagePath(String fileName) {
        Path normalized = imageDirectory.resolve(fileName).normalize();
        if (!normalized.startsWith(imageDirectory)) {
            throw new IllegalArgumentException("Invalid image file path");
        }
        return normalized;
    }

    public record ImageFileData(byte[] bytes, String contentType) {
    }
}
