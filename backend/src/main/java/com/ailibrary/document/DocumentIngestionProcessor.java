package com.ailibrary.document;

import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/**
 * Upload → Tika extraction → whitespace normalization → ~800-token chunks → tenant metadata → pgvector.
 * Status transitions are saved individually so clients polling the document see PROCESSING.
 */
@Service
public class DocumentIngestionProcessor {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionProcessor.class);
    static final int CHUNK_TOKENS = 800;
    static final int MIN_CHUNK_CHARS = 350;

    private final UserDocumentRepository docs;
    private final DocumentService service;
    private final VectorStoreAccess vectors;

    public DocumentIngestionProcessor(UserDocumentRepository docs, DocumentService service, VectorStoreAccess vectors) {
        this.docs = docs;
        this.service = service;
        this.vectors = vectors;
    }

    public void ingest(UUID id) {
        UserDocument doc = docs.findById(id).orElse(null);
        if (doc == null) return;
        doc.processing();
        docs.save(doc);
        try {
            VectorStore store = vectors.store()
                    .orElseThrow(() -> new IllegalStateException(
                            "AI/embeddings are disabled; enable AI_ENABLED to index documents"));
            List<Document> parsed = new TikaDocumentReader(new FileSystemResource(service.path(doc))).read();
            List<Document> chunks = chunk(doc, parsed);
            if (chunks.isEmpty()) throw new IllegalStateException("No text could be extracted from this file");
            store.add(chunks);
            doc.ready(chunks.size());
        } catch (RuntimeException ex) {
            log.warn("Ingestion failed for document {}: {}", id, ex.getMessage());
            doc.failed(ex.getMessage());
        }
        docs.save(doc);
    }

    static List<Document> chunk(UserDocument doc, List<Document> parsed) {
        List<Document> base = new ArrayList<>();
        for (Document page : parsed) {
            String text = normalizeWhitespace(page.getText());
            if (text.isBlank()) continue;
            base.add(Document.builder().text(text).metadata(baseMetadata(doc)).build());
        }
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_TOKENS)
                .withMinChunkSizeChars(MIN_CHUNK_CHARS)
                .withMinChunkLengthToEmbed(20)
                .withMaxNumChunks(5000)
                .withKeepSeparator(true)
                .build();
        List<Document> indexed = new ArrayList<>();
        int i = 0;
        for (Document c : splitter.apply(base)) {
            Map<String, Object> metadata = new HashMap<>(c.getMetadata());
            metadata.put("chunkIndex", i);
            indexed.add(Document.builder()
                    .id(chunkId(doc.getId(), i))
                    .text(c.getText())
                    .metadata(metadata)
                    .build());
            i++;
        }
        return indexed;
    }

    static Map<String, Object> baseMetadata(UserDocument doc) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", VectorFilters.TYPE_DOCUMENT_CHUNK);
        metadata.put("ownerId", doc.getOwnerId().toString());
        metadata.put("documentId", doc.getId().toString());
        metadata.put("sourceName", doc.getOriginalName());
        if (doc.getBookId() != null) metadata.put("bookId", doc.getBookId().toString());
        return metadata;
    }

    /** Collapses runs of spaces/tabs, trims lines and keeps at most one blank line between paragraphs. */
    static String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f\\u00A0 ]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    static String chunkId(UUID documentId, int index) {
        return UUID.nameUUIDFromBytes(("doc-" + documentId + "-" + index).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
