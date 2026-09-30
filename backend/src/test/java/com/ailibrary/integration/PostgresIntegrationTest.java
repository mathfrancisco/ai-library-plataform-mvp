package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for database-backed tests. Uses PostgreSQL + pgvector through Testcontainers, or an existing
 * database when TEST_DATABASE_URL (plus TEST_DATABASE_USERNAME/PASSWORD) is set. Skipped when neither exists.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.ai.enabled=false",
            "spring.ai.openai.api-key=disabled",
            "spring.ai.vectorstore.pgvector.initialize-schema=false",
            "app.auth.admin-emails=" + ApiIT.ADMIN_EMAIL
        })
abstract class PostgresIntegrationTest {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    record Res(int status, JsonNode body, HttpHeaders headers) {}

    static final String DATABASE_AVAILABLE = "com.ailibrary.integration.PostgresIntegrationTest#databaseAvailable";
    private static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");
    private static PostgreSQLContainer postgres;

    static boolean databaseAvailable() {
        // CI passes -Dtestcontainers.required=true so a missing Docker fails the build instead of skipping.
        if (Boolean.getBoolean("testcontainers.required")) return true;
        if (System.getenv("TEST_DATABASE_URL") != null) return true;
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String external = System.getenv("TEST_DATABASE_URL");
        if (external != null) {
            registry.add("spring.datasource.url", () -> external);
            registry.add("spring.datasource.username", () -> System.getenv()
                    .getOrDefault("TEST_DATABASE_USERNAME", "ailibrary"));
            registry.add("spring.datasource.password", () -> System.getenv()
                    .getOrDefault("TEST_DATABASE_PASSWORD", "ailibrary"));
            return;
        }
        synchronized (PostgresIntegrationTest.class) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer(IMAGE)
                        .withDatabaseName("ailibrary")
                        .withUsername("ailibrary")
                        .withPassword("ailibrary");
                postgres.start();
            }
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Registers a unique user and returns its access token. */
    String register(String name) throws Exception {
        Res res = call(
                "POST",
                "/api/auth/register",
                null,
                "{\"email\":\"" + name + "-" + UUID.randomUUID()
                        + "@example.com\",\"password\":\"password1\",\"displayName\":\"" + name + "\"}");
        assertThat(res.status()).isEqualTo(201);
        return res.body().path("accessToken").asString();
    }

    Res call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(
                        method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return send(b);
    }

    Res upload(String path, String token, String filename, String contentType, byte[] content) throws Exception {
        String boundary = "----it" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                        + "\"\r\nContent-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        return send(b);
    }

    private Res send(HttpRequest.Builder b) throws Exception {
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = r.body() == null || r.body().isBlank() ? JSON.missingNode() : JSON.readTree(r.body());
        return new Res(r.statusCode(), json, r.headers());
    }
}
