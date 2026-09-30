package com.ailibrary.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrieval tuning (SPEC-04 §9, §11.3). Thresholds are cosine similarity for all-MiniLM-L6-v2; tune them with the
 * evaluation set rather than by feel.
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        Integer documentTopK, Integer bookTopK, Double similarityThreshold, Integer maxContextTokens) {
    public RagProperties {
        if (documentTopK == null) documentTopK = 6;
        if (bookTopK == null) bookTopK = 8;
        if (similarityThreshold == null) similarityThreshold = 0.30;
        if (maxContextTokens == null) maxContextTokens = 4000;
    }
}
