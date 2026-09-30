package com.ailibrary.document;

import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
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
    private final UploadProperties props;

    public DocumentIngestionProcessor(
            UserDocumentRepository docs, DocumentService service, VectorStoreAccess vectors, UploadProperties props) {
        this.docs = docs;
        this.service = service;
        this.vectors = vectors;
        this.props = props;
    }

    public void ingest(UUID id) {
        UserDocument doc = docs.findById(id).orElse(null);
        if (doc == null || doc.getStatus() == DocumentStatus.READY) return;
        doc.processing();
        docs.save(doc);
        try {
            VectorStore store =
                    vectors.store().orElseThrow(() -> new IngestionException(IngestionFailure.VECTOR_DISABLED));
            List<Document> chunks = chunk(doc, read(doc));
            if (chunks.isEmpty()) throw new IngestionException(IngestionFailure.NO_TEXT);
            // Re-ingestion must not leave stale chunks behind when the new text is shorter.
            service.deleteVectors(doc.getOwnerId(), doc.getId());
            try {
                store.add(chunks);
            } catch (RuntimeException ex) {
                throw new IngestionException(IngestionFailure.EMBEDDING_FAILED, ex);
            }
            doc.ready(chunks.size());
        } catch (IngestionException ex) {
            log.warn("Ingestion failed for document {}: {}", id, ex.failure, ex.getCause());
            doc.failed(ex.failure.name());
        } catch (RuntimeException ex) {
            log.error("Ingestion failed for document {}", id, ex);
            doc.failed(IngestionFailure.UNKNOWN.name());
        }
        docs.save(doc);
    }

    /** Extraction is capped so a small archive cannot expand into hundreds of MB of text (SPEC-04 §12.9f). */
    private List<Document> read(UserDocument doc) {
        var handler = new BodyContentHandler(props.maxExtractedChars());
        try {
            return new TikaDocumentReader(
                            new FileSystemResource(service.path(doc)), handler, ExtractedTextFormatter.defaults())
                    .read();
        } catch (RuntimeException ex) {
            if (causedByWriteLimit(ex)) throw new IngestionException(IngestionFailure.TOO_LARGE_AFTER_EXTRACTION, ex);
            throw new IngestionException(IngestionFailure.UNSUPPORTED_FORMAT, ex);
        }
    }

    private static boolean causedByWriteLimit(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof WriteLimitReachedException) return true;
        }
        return false;
    }

    static final class IngestionException extends RuntimeException {
        final IngestionFailure failure;

        IngestionException(IngestionFailure failure) {
            this(failure, null);
        }

        IngestionException(IngestionFailure failure, Throwable cause) {
            super(failure.name(), cause);
            this.failure = failure;
        }
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
