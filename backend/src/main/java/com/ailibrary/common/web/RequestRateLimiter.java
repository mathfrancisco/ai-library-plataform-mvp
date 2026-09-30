package com.ailibrary.common.web;

import com.ailibrary.common.error.RateLimitException;
import com.ailibrary.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window limiter for public or expensive endpoints (login/register brute force, anonymous search).
 * Client IP comes from {@code getRemoteAddr()}; behind a proxy set {@code server.forward-headers-strategy=framework}
 * so it reflects X-Forwarded-For. Multi-instance deployments need a shared store.
 */
@Component
public class RequestRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int authPerMinute;
    private final int anonymousSearchPerMinute;
    private final int userSearchPerMinute;

    @Autowired
    public RequestRateLimiter(
            @Value("${app.rate-limit.auth-per-minute:10}") int authPerMinute,
            @Value("${app.rate-limit.anonymous-search-per-minute:30}") int anonymousSearchPerMinute,
            @Value("${app.rate-limit.user-search-per-minute:120}") int userSearchPerMinute) {
        this(authPerMinute, anonymousSearchPerMinute, userSearchPerMinute, Clock.systemUTC());
    }

    RequestRateLimiter(int authPerMinute, int anonymousSearchPerMinute, int userSearchPerMinute, Clock clock) {
        this.authPerMinute = authPerMinute;
        this.anonymousSearchPerMinute = anonymousSearchPerMinute;
        this.userSearchPerMinute = userSearchPerMinute;
        this.clock = clock;
    }

    /** Login/register: keyed by IP and (normalized) email so one attacker cannot lock out other users. */
    public void checkAuth(HttpServletRequest request, String action, String email) {
        String who = email == null ? "" : email.trim().toLowerCase();
        check(action + ":" + request.getRemoteAddr() + ":" + who, authPerMinute);
    }

    /** Search: per signed-in user, else per client IP. */
    public void checkSearch(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            check("search:user:" + p.id(), userSearchPerMinute);
        } else {
            check("search:ip:" + request.getRemoteAddr(), anonymousSearchPerMinute);
        }
    }

    void check(String key, int limit) {
        long minute = clock.instant().getEpochSecond() / 60;
        Window w = windows.computeIfAbsent(key, k -> new Window(minute));
        synchronized (w) {
            if (w.minute != minute) {
                w.minute = minute;
                w.count = 0;
            }
            if (++w.count > limit) throw new RateLimitException("Too many requests; try again in a minute");
        }
        if (windows.size() > 50_000) windows.entrySet().removeIf(e -> e.getValue().minute < minute - 2);
    }

    private static final class Window {
        long minute;
        int count;

        Window(long minute) {
            this.minute = minute;
        }
    }
}
