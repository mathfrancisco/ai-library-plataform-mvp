package com.ailibrary.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ailibrary.common.error.RateLimitException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiRateLimiterTest {
    @Test
    void enforcesPerUserFixedWindow() {
        var clock = new MutableClock(Instant.parse("2026-09-30T10:00:00Z"));
        var limiter = new AiRateLimiter(2, clock);
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        limiter.check(alice);
        limiter.check(alice);
        assertThatThrownBy(() -> limiter.check(alice)).isInstanceOf(RateLimitException.class);
        assertThatCode(() -> limiter.check(bob)).doesNotThrowAnyException();
        clock.now = clock.now.plusSeconds(60);
        assertThatCode(() -> limiter.check(alice)).doesNotThrowAnyException();
    }

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
