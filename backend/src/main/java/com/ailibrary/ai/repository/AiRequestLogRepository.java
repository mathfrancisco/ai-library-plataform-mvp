package com.ailibrary.ai.repository;

import com.ailibrary.ai.domain.AiRequestLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, UUID> {}
