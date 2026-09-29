package com.ailibrary.ai;

import com.ailibrary.common.error.RateLimitException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiRateLimiter {
    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerMinute = 30;
    public void check(UUID userId){
        long minute=Instant.now().getEpochSecond()/60;
        Window w=windows.computeIfAbsent(userId,k->new Window(minute));
        synchronized(w){
            if(w.minute!=minute){w.minute=minute;w.count=0;}
            if(++w.count>maxPerMinute) throw new RateLimitException("AI rate limit exceeded; try again shortly");
        }
        if(windows.size()>10_000) windows.entrySet().removeIf(e->e.getValue().minute<minute-5);
    }
    private static final class Window { long minute; int count; Window(long minute){this.minute=minute;} }
}
