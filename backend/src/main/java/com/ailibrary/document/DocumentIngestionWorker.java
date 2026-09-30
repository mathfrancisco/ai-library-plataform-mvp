package com.ailibrary.document;

import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Queues ingestion on the bounded {@code ingestionExecutor}. A full queue leaves the document STORED; the retry job
 * picks STORED documents up again, so uploads never fail because of load and restarts lose nothing.
 */
@Component
public class DocumentIngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionWorker.class);
    private final DocumentIngestionProcessor processor;
    private final UserDocumentRepository docs;
    private final Executor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public DocumentIngestionWorker(
            DocumentIngestionProcessor processor,
            UserDocumentRepository docs,
            @Qualifier("ingestionExecutor") Executor executor) {
        this.processor = processor;
        this.docs = docs;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpload(DocumentUploadedEvent event) {
        submit(event.documentId());
    }

    /** Documents interrupted by a restart are queued again on startup (SPEC-04 §8.7). */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterrupted() {
        List<UserDocument> pending = docs.findByStatusIn(List.of(DocumentStatus.STORED, DocumentStatus.PROCESSING));
        if (pending.isEmpty()) return;
        log.info("Resuming ingestion for {} interrupted document(s)", pending.size());
        pending.forEach(d -> submit(d.getId()));
    }

    /** Retries documents that stayed STORED, e.g. because the queue was full. */
    @Scheduled(fixedDelayString = "${app.upload.retry-interval:PT1M}", initialDelayString = "PT1M")
    public void retryStored() {
        docs.findByStatusAndCreatedAtBefore(DocumentStatus.STORED, Instant.now().minus(1, ChronoUnit.MINUTES))
                .forEach(d -> submit(d.getId()));
    }

    boolean submit(UUID documentId) {
        if (!inFlight.add(documentId)) return false;
        try {
            executor.execute(() -> {
                try {
                    processor.ingest(documentId);
                } finally {
                    inFlight.remove(documentId);
                }
            });
            return true;
        } catch (RejectedExecutionException full) { // includes Spring's TaskRejectedException
            inFlight.remove(documentId);
            log.info("Ingestion queue full; document {} stays STORED for retry", documentId);
            return false;
        }
    }
}
