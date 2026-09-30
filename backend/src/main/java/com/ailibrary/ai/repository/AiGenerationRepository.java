package com.ailibrary.ai.repository;

import com.ailibrary.ai.domain.AiGeneration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {
    Optional<AiGeneration> findByPromptHash(String promptHash);
}
