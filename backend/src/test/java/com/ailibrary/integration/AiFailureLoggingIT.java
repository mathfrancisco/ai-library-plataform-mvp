package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** A failing provider must leave a success=false request log (SPEC-04 §12.3) and map to AI_PROVIDER_ERROR. */
@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
@TestPropertySource(properties = "app.ai.enabled=true")
class AiFailureLoggingIT extends PostgresIntegrationTest {
    @TestConfiguration
    static class FailingChatModel {
        @Bean
        @Primary
        ChatModel failingChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    throw new IllegalStateException("provider exploded");
                }
            };
        }
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void failedCallIsLoggedAndMapped() throws Exception {
        String token = register("ai-fail");
        Res res = call("POST", "/api/ai/assistant", token, "{\"message\":\"What am I reading?\"}");
        assertThat(res.status()).isEqualTo(502);
        assertThat(res.body().path("code").asString()).isEqualTo("AI_PROVIDER_ERROR");
        String userId =
                call("GET", "/api/auth/me", token, null).body().path("id").asString();
        Integer failures = jdbc.queryForObject(
                "SELECT count(*) FROM ai_request_logs WHERE user_id = ?::uuid AND NOT success AND model IS NOT NULL",
                Integer.class,
                userId);
        assertThat(failures).isEqualTo(1);
    }
}
