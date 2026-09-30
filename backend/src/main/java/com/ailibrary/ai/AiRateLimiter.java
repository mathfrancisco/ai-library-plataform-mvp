package com.ailibrary.ai;

import com.ailibrary.common.error.AiException;
import com.ailibrary.common.error.RateLimitException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window limits: per user (app.ai.rate-limit-per-minute) and global
 * (app.ai.global-rate-per-minute), because provider quotas such as Groq's apply per API key.
 * Multi-instance deployments should move these counters to a shared store.
 */
@Component
public class AiRateLimiter {
    private static final UUID GLOBAL = new UUID(0, 0);
    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private final Window global = new Window(0);
    private final int maxPerMinute;
    private final int globalMaxPerMinute;
    private final Clock clock;

    @Autowired
    public AiRateLimiter(AiProperties properties) {
        this(properties.rateLimitPerMinute(), properties.globalRatePerMinute(), Clock.systemUTC());
    }

    AiRateLimiter(int maxPerMinute, Clock clock) {
        this(maxPerMinute, Integer.MAX_VALUE, clock);
    }

    AiRateLimiter(int maxPerMinute, int globalMaxPerMinute, Clock clock) {
        this.maxPerMinute = maxPerMinute;
        this.globalMaxPerMinute = globalMaxPerMinute;
        this.clock = clock;
    }

    public void check(UUID userId) {
        long minute = clock.instant().getEpochSecond() / 60;
        Window w = windows.computeIfAbsent(userId == null ? GLOBAL : userId, k -> new Window(minute));
        synchronized (w) {
            w.roll(minute);
            if (w.count + 1 > maxPerMinute) throw new RateLimitException("AI rate limit exceeded; try again shortly");
        }
        synchronized (global) {
            global.roll(minute);
            if (global.count + 1 > globalMaxPerMinute) throw AiException.rateLimited();
            global.count++;
        }
        synchronized (w) {
            w.count++;
        }
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> e.getValue().minute < minute - 5);
    }

    private static final class Window {
        long minute;
        int count;

        Window(long minute) {
            this.minute = minute;
        }

        void roll(long now) {
            if (minute != now) {
                minute = now;
                count = 0;
            }
        }
    }
}
