package com.ailibrary.document;

import com.ailibrary.document.domain.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentView(
        UUID id,
        UUID bookId,
        String originalName,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String errorMessage,
        int chunkCount,
        Instant createdAt) {}
