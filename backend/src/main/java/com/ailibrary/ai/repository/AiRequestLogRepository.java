package com.ailibrary.ai.repository;
import com.ailibrary.ai.domain.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, UUID> {}
