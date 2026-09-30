package com.ailibrary.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Upload and ingestion limits (SPEC-04 §8.10, §12.9f). */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        String dir, long maxBytes, Integer maxDocumentsPerUser, Long maxBytesPerUser, Integer maxExtractedChars) {
    public UploadProperties {
        if (maxDocumentsPerUser == null) maxDocumentsPerUser = 50;
        if (maxBytesPerUser == null) maxBytesPerUser = 500L * 1024 * 1024;
        if (maxExtractedChars == null) maxExtractedChars = 2_000_000;
    }
}
