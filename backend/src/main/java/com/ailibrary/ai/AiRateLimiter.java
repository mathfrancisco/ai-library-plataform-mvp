package com.ailibrary.ai;

import com.ailibrary.common.error.RateLimitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory per-user fixed-window limiter. Multi-instance deployments should move this to a shared store. */
@Component
public class AiRateLimiter {
    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerMinute;
    private final Clock clock;

    @Autowired
    public AiRateLimiter(AiProperties properties) {
        this(properties.maxRequestsPerMinute(), Clock.systemUTC());
    }

    AiRateLimiter(int maxPerMinute, Clock clock) {
        this.maxPerMinute = maxPerMinute;
        this.clock = clock;
    }

    public void check(UUID userId) {
        long minute = clock.instant().getEpochSecond() / 60;
        Window w = windows.computeIfAbsent(userId, k -> new Window(minute));
        synchronized (w) {
            if (w.minute != minute) { w.minute = minute; w.count = 0; }
            if (++w.count > maxPerMinute) throw new RateLimitException("AI rate limit exceeded; try again shortly");
        }
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> e.getValue().minute < minute - 5);
    }

    private static final class Window {
        long minute;
        int count;
        Window(long minute) { this.minute = minute; }
    }
}
