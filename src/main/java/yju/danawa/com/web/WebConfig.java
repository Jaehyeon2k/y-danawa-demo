package yju.danawa.com.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String crawlerImageDirectory;

    public WebConfig(
            @Value("${app.images.directory:${CRAWLER_OUT:c:/yjudanawa-damo/com/image-crawler}}") String crawlerImageDirectory) {
        this.crawlerImageDirectory = crawlerImageDirectory;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imageDir = Paths.get(crawlerImageDirectory).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(imageDir);
        } catch (java.io.IOException ignored) {
            // Keep serving config valid even if directory creation fails temporarily.
        }
        String location = imageDir.toUri().toString();

        registry.addResourceHandler("/images/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
