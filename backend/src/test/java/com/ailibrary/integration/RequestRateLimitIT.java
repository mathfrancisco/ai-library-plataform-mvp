package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.TestPropertySource;

@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
@TestPropertySource(properties = "app.rate-limit.anonymous-search-per-minute=5")
class RequestRateLimitIT extends PostgresIntegrationTest {
    @Test
    void anonymousSearchIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("GET", "/api/search?q=limit" + i + "&mode=SEMANTIC", null, null)
                            .status())
                    .isEqualTo(200);
        }
        Res limited = call("GET", "/api/search?q=over&mode=SEMANTIC", null, null);
        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.body().path("code").asString()).isEqualTo("RATE_LIMITED");
    }
}
