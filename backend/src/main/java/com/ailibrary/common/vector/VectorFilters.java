package com.ailibrary.common.vector;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.Objects;
import java.util.UUID;

/**
 * Metadata filters for the shared vector store. Private chunk filters always include the owner,
 * which is the RAG tenant-isolation invariant (see docs/07-security.md).
 */
public final class VectorFilters {
    public static final String TYPE_BOOK = "book";
    public static final String TYPE_DOCUMENT_CHUNK = "document_chunk";

    private VectorFilters() {}

    public static Filter.Expression books() {
        return new FilterExpressionBuilder().eq("type", TYPE_BOOK).build();
    }

    public static Filter.Expression documentChunks(UUID ownerId, UUID documentId) {
        return ownedChunks(ownerId, "documentId", documentId);
    }

    public static Filter.Expression bookChunks(UUID ownerId, UUID bookId) {
        return ownedChunks(ownerId, "bookId", bookId);
    }

    private static Filter.Expression ownedChunks(UUID ownerId, String scopeKey, UUID scopeId) {
        Objects.requireNonNull(ownerId, "ownerId is mandatory for private vector queries");
        Objects.requireNonNull(scopeId, scopeKey + " is mandatory");
        var b = new FilterExpressionBuilder();
        return b.and(
                b.and(b.eq("type", TYPE_DOCUMENT_CHUNK), b.eq("ownerId", ownerId.toString())),
                b.eq(scopeKey, scopeId.toString())
        ).build();
    }
}
