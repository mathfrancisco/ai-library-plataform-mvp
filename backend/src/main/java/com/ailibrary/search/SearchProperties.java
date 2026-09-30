package com.ailibrary.search;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Search tuning (SPEC-04 §6.6, §11.3, §12.8). Thresholds are for all-MiniLM-L6-v2 cosine similarity. */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        Double semanticThreshold, Duration semanticTimeout, Duration externalTimeout, Duration lexicalTimeout) {
    public SearchProperties {
        if (semanticThreshold == null) semanticThreshold = 0.30;
        if (semanticTimeout == null) semanticTimeout = Duration.ofSeconds(2);
        if (externalTimeout == null) externalTimeout = Duration.ofSeconds(3);
        if (lexicalTimeout == null) lexicalTimeout = Duration.ofSeconds(2);
    }
}
