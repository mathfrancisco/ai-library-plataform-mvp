package com.ailibrary.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.support.HashingEmbeddingModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * Small fixed corpus with question → expected chunk mappings (docs/11-testing-and-quality.md).
 * Uses a deterministic embedding so it runs in CI without a paid provider.
 */
class RagRetrievalEvaluationTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID aliceDoc = UUID.randomUUID();
    private final UUID bobDoc = UUID.randomUUID();
    private final UUID book = UUID.randomUUID();
    private SimpleVectorStore store;

    @BeforeEach
    void setUp() {
        store = SimpleVectorStore.builder(new HashingEmbeddingModel()).build();
        store.add(List.of(
                chunk(
                        "a-hex",
                        alice,
                        aliceDoc,
                        book,
                        "Hexagonal architecture isolates the domain core behind ports and adapters."),
                chunk(
                        "a-ddd",
                        alice,
                        aliceDoc,
                        book,
                        "Aggregates in domain driven design protect invariants inside a consistency boundary."),
                chunk(
                        "a-tests",
                        alice,
                        aliceDoc,
                        book,
                        "Unit tests should run fast and exercise behaviour, not implementation details."),
                // Bob's private copy talks about the same topic; it must never leak into Alice's answers.
                chunk(
                        "b-hex",
                        bob,
                        bobDoc,
                        book,
                        "Hexagonal architecture ports adapters secret notes from Bob about the domain core.")));
    }

    record Case(String question, String expectedChunk) {}

    @Test
    void recallAtKForExpectedChunks() {
        List<Case> cases = List.of(
                new Case("What does hexagonal architecture isolate with ports and adapters?", "a-hex"),
                new Case("How do aggregates protect invariants?", "a-ddd"),
                new Case("How fast should unit tests run?", "a-tests"));
        int hits = 0;
        for (Case c : cases) {
            List<Document> found = GroundedAnswerService.retrieve(
                    store,
                    c.question(),
                    new GroundedAnswerService.Retrieval(VectorFilters.documentChunks(alice, aliceDoc), 2, 0.0));
            if (found.stream().anyMatch(d -> d.getId().equals(c.expectedChunk()))) hits++;
        }
        assertThat((double) hits / cases.size()).isEqualTo(1.0);
    }

    @Test
    void documentChatNeverReturnsAnotherOwnersChunks() {
        List<Document> found = GroundedAnswerService.retrieve(
                store,
                "hexagonal architecture ports adapters domain core",
                new GroundedAnswerService.Retrieval(VectorFilters.documentChunks(alice, aliceDoc), 10, 0.0));
        assertThat(found).isNotEmpty();
        assertThat(found)
                .allSatisfy(d -> assertThat(d.getMetadata().get("ownerId")).isEqualTo(alice.toString()));
    }

    @Test
    void bookChatIsScopedToOwnerEvenWhenBookIsShared() {
        List<Document> found = GroundedAnswerService.retrieve(
                store,
                "hexagonal architecture secret notes",
                new GroundedAnswerService.Retrieval(VectorFilters.bookChunks(bob, book), 10, 0.0));
        assertThat(found).extracting(Document::getId).containsExactly("b-hex");
    }

    @Test
    void unknownDocumentReturnsNothing() {
        List<Document> found = GroundedAnswerService.retrieve(
                store,
                "hexagonal",
                new GroundedAnswerService.Retrieval(VectorFilters.documentChunks(alice, bobDoc), 10, 0.0));
        assertThat(found).isEmpty();
    }

    private static Document chunk(String id, UUID owner, UUID doc, UUID book, String text) {
        return Document.builder()
                .id(id)
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
                        id + ".md"))
                .build();
    }
}
