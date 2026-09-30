package com.ailibrary.ai;

import com.ailibrary.ai.domain.AiRequestLog;
import com.ailibrary.ai.repository.AiRequestLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes AI request logs in their own transaction so a caller's rollback never erases a failure record. */
@Component
public class AiRequestLogWriter {
    private final AiRequestLogRepository logs;

    public AiRequestLogWriter(AiRequestLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AiRequestLog log) {
        logs.save(log);
    }
}
