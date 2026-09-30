package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.support.HashingEmbeddingModel;
import java.util.ArrayList;
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

/**
 * Regression for SPEC-04 §12.1: HNSW filters after the index scan, so a tenant with few chunks among many
 * foreign chunks used to get an empty result. Iterative scans must keep user A's recall.
 */
@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class FilteredRetrievalRecallIT extends PostgresIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void smallTenantStillFindsItsChunksAmongThousandsOfOthers() {
        PgVectorStore store = PgVectorStore.builder(jdbc, new HashingEmbeddingModel(384))
                .dimensions(384)
                .initializeSchema(false)
                .build();
        UUID userA = UUID.randomUUID(), userB = UUID.randomUUID(), docA = UUID.randomUUID(), docB = UUID.randomUUID();
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            docs.add(chunk(userB, docB, i, "Hexagonal architecture ports adapters domain core notes number " + i));
        }
        for (int i = 0; i < 5; i++) {
            docs.add(chunk(userA, docA, i, "Hexagonal architecture keeps the domain core behind ports " + i));
        }
        try {
            for (int from = 0; from < docs.size(); from += 500) {
                store.add(docs.subList(from, Math.min(docs.size(), from + 500)));
            }
            List<Document> found = store.similaritySearch(SearchRequest.builder()
                    .query("hexagonal architecture ports adapters domain core")
                    .topK(5)
                    .similarityThreshold(0)
                    .filterExpression(VectorFilters.documentChunks(userA, docA))
                    .build());
            assertThat(found).hasSize(5);
            assertThat(found).allSatisfy(d -> assertThat(d.getMetadata()).containsEntry("ownerId", userA.toString()));
        } finally {
            jdbc.update(
                    "DELETE FROM vector_store WHERE metadata->>'ownerId' IN (?, ?)",
                    userA.toString(),
                    userB.toString());
        }
    }

    private static Document chunk(UUID owner, UUID doc, int index, String text) {
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
                        "chunkIndex",
                        index,
                        "sourceName",
                        "fixture.md"))
                .build();
    }
}
