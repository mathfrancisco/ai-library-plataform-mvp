package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Upload validation, real ingestion with local embeddings, failure reasons and owner isolation over HTTP. */
@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class DocumentIT extends PostgresIntegrationTest {
    private static final byte[] TEXT = ("Hexagonal architecture keeps the domain core independent of adapters. "
                    + "Ports define what the core needs; adapters implement those ports for databases and web.\n")
            .repeat(20)
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void uploadIngestAndIsolateFromOtherUsers() throws Exception {
        String alice = register("doc-alice");
        String bob = register("doc-bob");

        Res uploaded = upload("/api/documents", alice, "notes.txt", "text/plain", TEXT);
        assertThat(uploaded.status()).isEqualTo(200);
        String id = uploaded.body().path("id").asString();
        Res ready = awaitStatus(alice, id);
        assertThat(ready.body().path("status").asString()).isEqualTo("READY");
        assertThat(ready.body().path("chunkCount").asInt()).isPositive();

        assertThat(call("GET", "/api/documents", bob, null).body().size()).isZero();
        for (String[] req : new String[][] {
            {"GET", "/api/documents/" + id, null},
            {"POST", "/api/documents/" + id + "/chat", "{\"question\":\"What are ports?\"}"},
            {"POST", "/api/documents/" + id + "/reingest", null},
            {"DELETE", "/api/documents/" + id, null}
        }) {
            Res res = call(req[0], req[1], bob, req[2]);
            assertThat(res.status()).as(req[0] + " " + req[1]).isEqualTo(404);
            assertThat(res.body().path("code").asString()).isEqualTo("DOCUMENT_NOT_FOUND");
        }

        // AI is disabled in tests: retrieval runs, then the chat model reports AI_DISABLED.
        Res chat = call("POST", "/api/documents/" + id + "/chat", alice, "{\"question\":\"What are ports?\"}");
        assertThat(chat.status()).isEqualTo(503);
        assertThat(chat.body().path("code").asString()).isEqualTo("AI_DISABLED");

        assertThat(call("DELETE", "/api/documents/" + id, alice, null).status()).isEqualTo(204);
    }

    @Test
    void rejectsWrongMagicBytesAndEmptyFiles() throws Exception {
        String token = register("doc-validate");
        Res html = upload(
                "/api/documents",
                token,
                "paper.pdf",
                "application/pdf",
                "<html><body>x</body></html>".getBytes(StandardCharsets.UTF_8));
        assertThat(html.status()).isEqualTo(400);
        assertThat(html.body().path("code").asString()).isEqualTo("UNSUPPORTED_FILE_TYPE");
        Res empty = upload("/api/documents", token, "empty.txt", "text/plain", new byte[0]);
        assertThat(empty.body().path("code").asString()).isEqualTo("EMPTY_FILE");
    }

    @Test
    void corruptPdfEndsFailedWithASafeReason() throws Exception {
        String token = register("doc-corrupt");
        byte[] corrupt = "%PDF-1.7\n1 0 obj << /Broken".getBytes(StandardCharsets.UTF_8);
        String id = upload("/api/documents", token, "broken.pdf", "application/pdf", corrupt)
                .body()
                .path("id")
                .asString();
        Res done = awaitStatus(token, id);
        assertThat(done.body().path("status").asString()).isEqualTo("FAILED");
        assertThat(done.body().path("failureReason").asString()).isIn("UNSUPPORTED_FORMAT", "NO_TEXT");
        assertThat(done.body().path("errorMessage").asString())
                .doesNotContain("Exception")
                .doesNotContain("/");
    }

    private Res awaitStatus(String token, String id) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (true) {
            Res res = call("GET", "/api/documents/" + id, token, null);
            String status = res.body().path("status").asString();
            if (status.equals("READY")
                    || status.equals("FAILED")
                    || Instant.now().isAfter(deadline)) return res;
            Thread.sleep(250);
        }
    }
}
