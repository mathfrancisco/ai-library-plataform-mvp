package com.ailibrary.document;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Small bounded pool: parsing and local embeddings are CPU-heavy (SPEC-04 §8.6). */
@Configuration
public class IngestionExecutorConfig {
    /** Not a default candidate, so @Async and Boot's applicationTaskExecutor are unaffected. */
    @Bean(defaultCandidate = false)
    ThreadPoolTaskExecutor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
