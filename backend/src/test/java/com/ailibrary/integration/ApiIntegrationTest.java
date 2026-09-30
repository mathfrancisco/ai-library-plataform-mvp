package com.ailibrary.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP-level checks for auth boundaries, owner isolation and the error envelope. */
@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class ApiIntegrationTest extends PostgresIntegrationTest {
    static final String ADMIN_EMAIL = "admin-it@example.com";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    record Res(int status, JsonNode body) {}

    @Test
    void privateEndpointsRequireAuthenticationWithEnvelope() throws Exception {
        Res res = call("GET", "/api/library", null, null);
        assertThat(res.status()).isEqualTo(401);
        assertThat(res.body().path("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(res.body().path("path").asString()).isEqualTo("/api/library");
    }

    @Test
    void libraryReadingAndDocumentsAreIsolatedPerUser() throws Exception {
        String alice = register("alice");
        String bob = register("bob");
        Res book = call("POST", "/api/books", alice, "{\"title\":\"Isolation " + UUID.randomUUID() + "\",\"authorNames\":\"A\",\"pageCount\":200}");
        assertThat(book.status()).isEqualTo(200);
        String bookId = book.body().path("id").asString();

        assertThat(call("POST", "/api/library/books/" + bookId, alice, "{\"status\":\"READING\",\"rating\":4}").status()).isEqualTo(200);
        Res progress = call("GET", "/api/reading/" + bookId, alice, null);
        assertThat(progress.body().path("startedAt").isNull()).isFalse();

        assertThat(call("GET", "/api/library", bob, null).body().size()).isZero();
        assertThat(call("GET", "/api/reading/" + bookId, bob, null).status()).isEqualTo(404);
        assertThat(call("DELETE", "/api/library/books/" + bookId, bob, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/library", alice, null).body().size()).isEqualTo(1);

        Res dashboard = call("GET", "/api/dashboard", alice, null);
        assertThat(dashboard.body().path("reading").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/dashboard", bob, null).body().path("totalBooks").asInt()).isZero();
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
        assertThat(aiOff.body().path("code").asString()).isEqualTo("AI_DISABLED");
    }

    @Test
    void adminEndpointsRequireAdminRoleAndConfiguredAdminsArePromoted() throws Exception {
        String user = register("plain");
        Res forbidden = call("POST", "/api/admin/books/reindex", user, null);
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().path("code").asString()).isEqualTo("FORBIDDEN");

        call("POST", "/api/auth/register", null, "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"password1\",\"displayName\":\"Admin\"}");
        Res login = call("POST", "/api/auth/login", null, "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"password1\"}");
        assertThat(login.body().path("user").path("role").asString()).isEqualTo("ADMIN");
        Res reindex = call("POST", "/api/admin/books/reindex", login.body().path("accessToken").asString(), null);
        // AI is disabled in tests, so the admin gets past authorization and hits the AI guard.
        assertThat(reindex.body().path("code").asString()).isEqualTo("AI_DISABLED");
    }

    private String register(String name) throws Exception {
        Res res = call("POST", "/api/auth/register", null,
                "{\"email\":\"" + name + "-" + UUID.randomUUID() + "@example.com\",\"password\":\"password1\",\"displayName\":\"" + name + "\"}");
        assertThat(res.status()).isEqualTo(201);
        return res.body().path("accessToken").asString();
    }

    private Res call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Res(r.statusCode(), r.body() == null || r.body().isBlank() ? JSON.missingNode() : JSON.readTree(r.body()));
    }
}
