package com.ailibrary.document;

/**
 * Safe, user-facing ingestion failure reasons. Only the code is stored; the full exception is logged server-side
 * and never returned (SPEC-04 §8.3).
 */
public enum IngestionFailure {
    NO_TEXT("No text could be extracted from this file."),
    UNSUPPORTED_FORMAT("The file could not be parsed. It may be corrupt or password-protected."),
    TOO_LARGE_AFTER_EXTRACTION("The extracted text is too large to index."),
    EMBEDDING_FAILED("Indexing failed while computing embeddings. Try again later."),
    VECTOR_DISABLED("Vector indexing is disabled on this server."),
    UNKNOWN("Ingestion failed unexpectedly. Try again later.");

    private final String message;

    IngestionFailure(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    /** Stored values from before this enum existed are free text; show them as UNKNOWN. */
    public static IngestionFailure fromStored(String stored) {
        if (stored == null) return null;
        try {
            return valueOf(stored);
        } catch (IllegalArgumentException legacy) {
            return UNKNOWN;
        }
    }
}
