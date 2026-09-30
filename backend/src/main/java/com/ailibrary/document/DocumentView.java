package com.ailibrary.document;

import com.ailibrary.document.domain.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

/** {@code failureReason} is a stable code; {@code errorMessage} is its user-facing text. */
public record DocumentView(
        UUID id,
        UUID bookId,
        String originalName,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String failureReason,
        String errorMessage,
        int chunkCount,
        Instant createdAt) {}
