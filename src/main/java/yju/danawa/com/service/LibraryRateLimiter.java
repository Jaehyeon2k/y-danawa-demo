package yju.danawa.com.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class LibraryRateLimiter {

    private static final int WINDOW_SEC = 60;
    private static final int MAX_REQUESTS = 40;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean allow(String key) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> q = windows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        long cutoff = now - WINDOW_SEC;

        while (true) {
            Long head = q.peekFirst();
            if (head == null || head >= cutoff) break;
            q.pollFirst();
        }

        if (q.size() >= MAX_REQUESTS) {
            return false;
        }
        q.addLast(now);
        return true;
    }
}
