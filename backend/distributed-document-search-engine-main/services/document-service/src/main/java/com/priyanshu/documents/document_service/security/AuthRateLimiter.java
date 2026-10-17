package com.priyanshu.documents.document_service.security;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Per-instance bounded limiter; production clusters also need shared edge limits. */
@Component
public class AuthRateLimiter {
    private record Window(long until, int count) {}
    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void check(String key, int limit, Duration duration) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().until() <= now);
        String hashedKey = AccountSecrets.hash(key);
        Window window = windows.get(hashedKey);
        if ((window != null && window.count() >= limit) || (window == null && windows.size() >= 10000))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
        windows.put(hashedKey, new Window(window == null ? now + duration.toMillis() : window.until(),
                window == null ? 1 : window.count() + 1));
    }
}
