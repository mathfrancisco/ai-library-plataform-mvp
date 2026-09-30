package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.support.HashingEmbeddingModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the application's metadata filters through the real pgvector SQL converter and schema. */
@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class RagIsolationIT extends PostgresIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void ownerAndDocumentFiltersAreEnforcedByPgvector() {
        PgVectorStore store = PgVectorStore.builder(jdbc, new HashingEmbeddingModel(384))
                .dimensions(384)
                .initializeSchema(false)
                .build();
        UUID alice = UUID.randomUUID(),
                bob = UUID.randomUUID(),
                aliceDoc = UUID.randomUUID(),
                bobDoc = UUID.randomUUID(),
                book = UUID.randomUUID();
        store.add(List.of(
                chunk(alice, aliceDoc, book, "Ports and adapters keep the hexagonal domain core isolated."),
                chunk(bob, bobDoc, book, "Bob private notes about ports and adapters in hexagonal architecture.")));

        List<Document> aliceHits = store.similaritySearch(SearchRequest.builder()
                .query("hexagonal ports adapters")
                .topK(10)
                .similarityThreshold(0)
                .filterExpression(VectorFilters.documentChunks(alice, aliceDoc))
                .build());
        assertThat(aliceHits).isNotEmpty().allSatisfy(d -> assertThat(d.getMetadata())
                .containsEntry("ownerId", alice.toString()));

        List<Document> bobBook = store.similaritySearch(SearchRequest.builder()
                .query("hexagonal ports adapters")
                .topK(10)
                .similarityThreshold(0)
                .filterExpression(VectorFilters.bookChunks(bob, book))
                .build());
        assertThat(bobBook).singleElement().satisfies(d -> assertThat(d.getMetadata())
                .containsEntry("documentId", bobDoc.toString()));

        List<Document> crossTenant = store.similaritySearch(SearchRequest.builder()
                .query("hexagonal")
                .topK(10)
                .similarityThreshold(0)
                .filterExpression(VectorFilters.documentChunks(alice, bobDoc))
                .build());
        assertThat(crossTenant).isEmpty();

        jdbc.update("DELETE FROM vector_store WHERE metadata->>'ownerId' IN (?, ?)", alice.toString(), bob.toString());
    }

    private static Document chunk(UUID owner, UUID doc, UUID book, String text) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(Map.of(
                        "type",
                        VectorFilters.TYPE_DOCUMENT_CHUNK,
                        "ownerId",
                        owner.toString(),
                        "documentId",
                        doc.toString(),
                        "bookId",
                        book.toString(),
                        "chunkIndex",
                        0,
                        "sourceName",
                        "fixture.md"))
                .build();
    }
}
