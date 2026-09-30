package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class SchemaIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesCoreAndVectorTablesAndIndexes() {
        for (String table : new String[] {
            "users",
            "books",
            "user_library",
            "reading_progress",
            "documents",
            "ai_generations",
            "ai_request_logs",
            "vector_store"
        }) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?", Integer.class, table);
            assertThat(count).as(table).isEqualTo(1);
        }
        Integer indexes = jdbc.queryForObject(
                """
                select count(*) from pg_indexes
                where indexname in ('idx_vector_store_embedding_hnsw', 'idx_vector_store_metadata_gin', 'idx_books_search_document')
                """,
                Integer.class);
        assertThat(indexes).isEqualTo(3);
    }

    @Test
    void fullTextSearchUsesGeneratedDocument() {
        jdbc.update(
                "insert into books(title, author_names, description) values ('Schema FTS probe', 'Tester', 'unique-marker-xyzzy')");
        Integer hits = jdbc.queryForObject(
                "select count(*) from books where search_document @@ websearch_to_tsquery('simple', 'xyzzy')",
                Integer.class);
        assertThat(hits).isGreaterThanOrEqualTo(1);
    }
}
