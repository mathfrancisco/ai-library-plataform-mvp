package com.ailibrary.document;

import com.ailibrary.document.domain.UserDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIngestionProcessorTest {
    @Test
    void normalizesWhitespaceButKeepsParagraphs() {
        assertThat(DocumentIngestionProcessor.normalizeWhitespace("  Title\t\t here \r\n\r\n\r\n\r\nBody\u00A0 text  \n"))
                .isEqualTo("Title here\n\nBody text");
        assertThat(DocumentIngestionProcessor.normalizeWhitespace(null)).isEmpty();
    }

    @Test
    void chunksCarryTenantMetadataAndStableUuidIds() {
        UUID owner = UUID.randomUUID(), book = UUID.randomUUID();
        UserDocument doc = new UserDocument(owner, book, "notes.md", "text/markdown", 10, owner + "/x.md");
        String longText = "Hexagonal architecture separates the domain from adapters. ".repeat(200);
        List<Document> chunks = DocumentIngestionProcessor.chunk(doc, List.of(new Document(longText), new Document("   ")));
        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            Document c = chunks.get(i);
            assertThat(c.getMetadata()).containsEntry("ownerId", owner.toString())
                    .containsEntry("documentId", doc.getId().toString())
                    .containsEntry("bookId", book.toString())
                    .containsEntry("type", "document_chunk")
                    .containsEntry("chunkIndex", i);
            assertThat(UUID.fromString(c.getId())).isEqualTo(UUID.fromString(DocumentIngestionProcessor.chunkId(doc.getId(), i)));
        }
    }
}
