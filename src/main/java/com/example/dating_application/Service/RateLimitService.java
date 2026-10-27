package com.example.dating_application.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ліміт невдалих спроб для /login і /verify. Лічильник у пам'яті — розрахунок на один інстанс. */
@Service
public class RateLimitService {

    private record Window(int count, Instant expiresAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Duration window;

    public RateLimitService(@Value("${app.ratelimit.window-minutes:15}") long windowMinutes) {
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public boolean isLimited(String key, int maxAttempts) {
        Window w = windows.get(key);
        if (w == null) {
            return false;
        }
        if (Instant.now().isAfter(w.expiresAt())) {
            windows.remove(key, w);
            return false;
        }
        return w.count() >= maxAttempts;
    }

    /** compute, щоб паралельні запити не загубили інкремент. */
    public void recordAttempt(String key) {
        Instant now = Instant.now();
        windows.compute(key, (k, w) ->
                (w == null || now.isAfter(w.expiresAt()))
                        ? new Window(1, now.plus(window))
                        : new Window(w.count() + 1, w.expiresAt()));
    }

    public void recordFailure(String key) {
        recordAttempt(key);
    }

    public void reset(String key) {
        windows.remove(key);
    }

    public long secondsUntilReset(String key) {
        Window w = windows.get(key);
        if (w == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), w.expiresAt()).getSeconds();
        return Math.max(seconds, 0);
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 10 * 60 * 1000L)
    public void purgeExpired() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
