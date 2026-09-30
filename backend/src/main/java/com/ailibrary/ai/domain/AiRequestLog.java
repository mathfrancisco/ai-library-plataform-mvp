package com.ailibrary.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_request_logs")
public class AiRequestLog {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String operation;

    private String provider;
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AiRequestLog() {}

    public AiRequestLog(
            UUID userId,
            String operation,
            String provider,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            long latencyMs,
            boolean success,
            String errorType) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.operation = operation;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.success = success;
        this.errorType = errorType;
    }
}
