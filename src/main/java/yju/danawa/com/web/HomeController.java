package yju.danawa.com.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "status", "ok",
                "service", "Y-Danawa",
                "timestamp", Instant.now().toString()
        );
    }
}
