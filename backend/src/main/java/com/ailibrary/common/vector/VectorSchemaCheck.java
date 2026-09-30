package com.ailibrary.common.vector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fails fast when the configured embedding size does not match the vector column, instead of failing on the
 * first insert. AI_EMBEDDING_DIMENSIONS is the single source of truth.
 */
@Component
public class VectorSchemaCheck {
    private final JdbcTemplate jdbc;
    private final int configuredDimensions;
    private final boolean enabled;

    public VectorSchemaCheck(
            JdbcTemplate jdbc,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:384}") int configuredDimensions,
            @Value("${app.vector.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.configuredDimensions = configuredDimensions;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (!enabled) return;
        Integer column = jdbc.queryForObject(
                """
                SELECT atttypmod FROM pg_attribute
                WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'
                """,
                Integer.class);
        check(configuredDimensions, column);
    }

    static void check(int configured, Integer column) {
        if (column == null || column != configured) {
            throw new IllegalStateException("Embedding dimension mismatch: AI_EMBEDDING_DIMENSIONS=" + configured
                    + " but vector_store.embedding is vector(" + column + "). Add a migration or fix the setting.");
        }
    }
}
