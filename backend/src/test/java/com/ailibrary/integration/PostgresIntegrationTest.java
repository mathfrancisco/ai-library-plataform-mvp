package com.ailibrary.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for database-backed tests. Uses PostgreSQL + pgvector through Testcontainers, or an existing
 * database when TEST_DATABASE_URL (plus TEST_DATABASE_USERNAME/PASSWORD) is set. Skipped when neither exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ai.enabled=false",
        "spring.ai.openai.api-key=disabled",
        "spring.ai.vectorstore.pgvector.initialize-schema=false",
        "app.auth.admin-emails=" + ApiIntegrationTest.ADMIN_EMAIL
})
abstract class PostgresIntegrationTest {
    static final String DATABASE_AVAILABLE = "com.ailibrary.integration.PostgresIntegrationTest#databaseAvailable";
    private static final DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");
    private static PostgreSQLContainer postgres;

    static boolean databaseAvailable() {
        // CI passes -Dtestcontainers.required=true so a missing Docker fails the build instead of skipping.
        if (Boolean.getBoolean("testcontainers.required")) return true;
        if (System.getenv("TEST_DATABASE_URL") != null) return true;
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String external = System.getenv("TEST_DATABASE_URL");
        if (external != null) {
            registry.add("spring.datasource.url", () -> external);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "ailibrary"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "ailibrary"));
            return;
        }
        synchronized (PostgresIntegrationTest.class) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer(IMAGE).withDatabaseName("ailibrary").withUsername("ailibrary").withPassword("ailibrary");
                postgres.start();
            }
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
