package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** HTTP-level checks for auth boundaries, owner isolation and the error envelope. */
@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class ApiIT extends PostgresIntegrationTest {
    static final String ADMIN_EMAIL = "admin-it@example.com";

    @Test
    void privateEndpointsRequireAuthenticationWithEnvelope() throws Exception {
        Res res = call("GET", "/api/library", null, null);
        assertThat(res.status()).isEqualTo(401);
        assertThat(res.body().path("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(res.body().path("path").asString()).isEqualTo("/api/library");
    }

    @Test
    void libraryReadingAndDocumentsAreIsolatedPerUser() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        Res book = call(
                "POST",
                "/api/books",
                alice,
                "{\"title\":\"Isolation " + UUID.randomUUID() + "\",\"authorNames\":\"A\",\"pageCount\":200}");
        assertThat(book.status()).isEqualTo(201);
        String bookId = book.body().path("id").asString();

        assertThat(call("POST", "/api/library/books/" + bookId, alice, "{\"status\":\"READING\",\"rating\":4}")
                        .status())
                .isEqualTo(200);
        Res progress = call("GET", "/api/reading/" + bookId, alice, null);
        assertThat(progress.body().path("startedAt").isNull()).isFalse();

        assertThat(call("GET", "/api/library", bob, null).body().size()).isZero();
        Res bobProgress = call("GET", "/api/reading/" + bookId, bob, null);
        assertThat(bobProgress.status()).isEqualTo(200);
        assertThat(bobProgress.body().path("exists").asBoolean()).isFalse();
        assertThat(call("DELETE", "/api/library/books/" + bookId, bob, null).status())
                .isEqualTo(404);
        assertThat(call("GET", "/api/library", alice, null).body().size()).isEqualTo(1);

        Res dashboard = call("GET", "/api/dashboard", alice, null);
        assertThat(dashboard.body().path("reading").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/dashboard", bob, null)
                        .body()
                        .path("totalBooks")
                        .asInt())
                .isZero();
    }

    @Test
    void errorEnvelopeCarriesSpecificCodes() throws Exception {
        String token = register("errors");
        Res missing = call("GET", "/api/books/" + UUID.randomUUID(), token, null);
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.body().path("code").asString()).isEqualTo("BOOK_NOT_FOUND");
        Res invalid = call("PATCH", "/api/library/books/" + UUID.randomUUID(), token, "{\"rating\":9}");
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().path("code").asString()).isEqualTo("VALIDATION_ERROR");
        Res aiOff = call("POST", "/api/ai/assistant", token, "{\"message\":\"hi\"}");
        assertThat(aiOff.status()).isEqualTo(503);
        assertThat(aiOff.body().path("code").asString()).isEqualTo("AI_DISABLED");
    }

    @Test
    void adminEndpointsRequireAdminRoleAndConfiguredAdminsArePromoted() throws Exception {
        String user = register("plain");
        Res forbidden = call("POST", "/api/admin/books/reindex", user, null);
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().path("code").asString()).isEqualTo("FORBIDDEN");

        call(
                "POST",
                "/api/auth/register",
                null,
                "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"password1\",\"displayName\":\"Admin\"}");
        Res login =
                call("POST", "/api/auth/login", null, "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"password1\"}");
        assertThat(login.body().path("user").path("role").asString()).isEqualTo("ADMIN");
        Res reindex = call(
                "POST",
                "/api/admin/books/reindex",
                login.body().path("accessToken").asString(),
                null);
        assertThat(reindex.status()).isEqualTo(200);
        assertThat(reindex.body().path("indexed").asInt()).isGreaterThanOrEqualTo(1);
    }
}
