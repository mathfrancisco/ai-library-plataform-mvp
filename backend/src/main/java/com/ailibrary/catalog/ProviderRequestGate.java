package com.ailibrary.catalog;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/** Small single-node pacing guard for provider courtesy limits. */
public class ProviderRequestGate {
    private final long minIntervalNanos;
    private final AtomicLong nextAllowed = new AtomicLong();

    public ProviderRequestGate(long minIntervalMillis) {
        this.minIntervalNanos = minIntervalMillis * 1_000_000L;
    }

    public void awaitTurn() {
        while (true) {
            long now = System.nanoTime();
            long current = nextAllowed.get();
            long scheduled = Math.max(now, current);
            if (nextAllowed.compareAndSet(current, scheduled + minIntervalNanos)) {
                long wait = scheduled - now;
                if (wait > 0) LockSupport.parkNanos(wait);
                return;
            }
        }
    }
}
