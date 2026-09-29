package com.ailibrary.ai.repository;
import com.ailibrary.ai.domain.AiGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> { Optional<AiGeneration> findByPromptHash(String promptHash); }
