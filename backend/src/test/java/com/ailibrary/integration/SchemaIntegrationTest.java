package com.ailibrary.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.ai.enabled=false",
        "spring.ai.openai.api-key=disabled",
        "spring.ai.vectorstore.pgvector.initialize-schema=false"
})
class SchemaIntegrationTest {
    private static final DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("ailibrary")
            .withUsername("ailibrary")
            .withPassword("ailibrary");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreatesCoreAndVectorTables() {
        Integer books = jdbc.queryForObject("select count(*) from information_schema.tables where table_name='books'", Integer.class);
        Integer vectors = jdbc.queryForObject("select count(*) from information_schema.tables where table_name='vector_store'", Integer.class);
        assertThat(books).isEqualTo(1);
        assertThat(vectors).isEqualTo(1);
    }
}
