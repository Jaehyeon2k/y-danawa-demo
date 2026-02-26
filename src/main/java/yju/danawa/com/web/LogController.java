package yju.danawa.com.web;

import yju.danawa.com.domain.ClickLog;
import yju.danawa.com.repository.ClickLogRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final ClickLogRepository clickLogRepository;
    private final String apiKey;

    public LogController(ClickLogRepository clickLogRepository,
                         @Value("${app.api-key:}") String apiKey) {
        this.clickLogRepository = clickLogRepository;
        this.apiKey = apiKey;
    }

    @PostMapping("/click")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> sendClickLog(@Valid @RequestBody ClickLogRequest request,
                                            @RequestHeader(value = "X-API-KEY", required = false) String requestApiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            if (requestApiKey == null || !apiKey.equals(requestApiKey)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
            }
        }
        ClickLog log = new ClickLog(
                request.isbn(),
                request.target_channel(),
                request.slider_value(),
                LocalDateTime.now()
        );
        ClickLog saved = clickLogRepository.save(log);

        return Map.of(
                "status", "saved",
                "click_id", saved.getClickId()
        );
    }
}
