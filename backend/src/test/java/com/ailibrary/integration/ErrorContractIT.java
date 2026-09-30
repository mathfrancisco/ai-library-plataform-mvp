package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class ErrorContractIT extends PostgresIntegrationTest {
    @Autowired
    ApplicationContext context;

    @Test
    void everyErrorUsesTheEnvelopeWithAStableCodeAndRequestId() throws Exception {
        Res anonymous = call("GET", "/api/library", null, null);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.body().path("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(anonymous.body().path("requestId").asString()).isNotBlank();
        assertThat(anonymous.headers().firstValue("X-Request-Id")).isPresent();

        String token = register("errors");
        Res badUuid = call("GET", "/api/books/not-a-uuid", token, null);
        assertThat(badUuid.status()).isEqualTo(400);
        assertThat(badUuid.body().path("code").asString()).isEqualTo("VALIDATION_ERROR");

        Res book = call("POST", "/api/books", token, "{\"title\":\"Envelope " + UUID.randomUUID() + "\"}");
        String bookId = book.body().path("id").asString();
        call("POST", "/api/library/books/" + bookId, token, "{}");
        Res badEnum = call("PATCH", "/api/library/books/" + bookId, token, "{\"status\":\"NOPE\"}");
        assertThat(badEnum.status()).isEqualTo(400);
        assertThat(badEnum.body().path("message").asString()).isEqualTo("status: invalid value");

        Res tooLarge = upload("/api/documents", token, "big.txt", "text/plain", new byte[26 * 1024 * 1024]);
        assertThat(tooLarge.status()).isEqualTo(413);
        assertThat(tooLarge.body().path("code").asString()).isEqualTo("FILE_TOO_LARGE");

        Res missing = call("GET", "/api/books/" + UUID.randomUUID(), token, null);
        assertThat(missing.body().path("code").asString()).isEqualTo("BOOK_NOT_FOUND");
    }

    @Test
    void ingestionExecutorDoesNotReplaceTheDefaultTaskExecutor() {
        assertThat(context.containsBean("applicationTaskExecutor")).isTrue();
        assertThat(context.containsBean("ingestionExecutor")).isTrue();
    }
}
