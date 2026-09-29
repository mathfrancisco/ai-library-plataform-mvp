package com.ailibrary.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="ai_generations")
public class AiGeneration {
    @Id private UUID id;
    @Column(name="user_id") private UUID userId;
    @Column(name="entity_type", nullable=false) private String entityType;
    @Column(name="entity_id") private UUID entityId;
    @Column(name="prompt_type", nullable=false) private String promptType;
    @Column(name="prompt_hash", nullable=false, unique=true, length=64) private String promptHash;
    private String provider;
    private String model;
    @Column(nullable=false, columnDefinition="text") private String result;
    @Column(name="created_at", nullable=false, insertable=false, updatable=false) private Instant createdAt;
    protected AiGeneration() {}
    public AiGeneration(UUID userId,String entityType,UUID entityId,String promptType,String promptHash,String provider,String model,String result){
        this.id=UUID.randomUUID(); this.userId=userId; this.entityType=entityType; this.entityId=entityId; this.promptType=promptType;
        this.promptHash=promptHash; this.provider=provider; this.model=model; this.result=result;
    }
    public String getResult(){ return result; }
}
