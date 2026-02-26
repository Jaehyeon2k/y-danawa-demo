package yju.danawa.com.web;

import yju.danawa.com.domain.BookImage;
import yju.danawa.com.service.CrawlerImageFileService;
import yju.danawa.com.service.BookImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/images")
public class BookImageController {

    private final BookImageService bookImageService;
    private final CrawlerImageFileService crawlerImageFileService;
    private final String placeholderImageUrl;

    public BookImageController(
            BookImageService bookImageService,
            CrawlerImageFileService crawlerImageFileService,
            @Value("${app.placeholder-image-url:https://placehold.co/120x174?text=%EC%9D%B4%EB%AF%B8%EC%A7%80+%EC%97%86%EC%9D%8C}") String placeholderImageUrl
    ) {
        this.bookImageService = bookImageService;
        this.crawlerImageFileService = crawlerImageFileService;
        this.placeholderImageUrl = placeholderImageUrl;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getImage(@PathVariable("id") Long id) {
        BookImage image = bookImageService.findById(id).orElse(null);
        if (image == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, placeholderImageUrl)
                    .build();
        }

        String contentType = image.getContentType();
        MediaType mediaType = contentType != null ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(image.getBytes());
    }

    @GetMapping("/by-name/{fileName:.+}")
    public ResponseEntity<byte[]> getImageByFileName(@PathVariable("fileName") String fileName) {
        CrawlerImageFileService.ImageFileData image;
        try {
            image = crawlerImageFileService.readImage(fileName).orElse(null);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        if (image == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, placeholderImageUrl)
                    .build();
        }

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(image.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(image.bytes());
    }
}
