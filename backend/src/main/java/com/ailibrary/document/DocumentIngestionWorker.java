package com.ailibrary.document;

import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class DocumentIngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionWorker.class);
    private final DocumentIngestionProcessor processor;
    private final UserDocumentRepository docs;

    public DocumentIngestionWorker(DocumentIngestionProcessor processor, UserDocumentRepository docs) {
        this.processor = processor;
        this.docs = docs;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpload(DocumentUploadedEvent event) {
        processor.ingest(event.documentId());
    }

    /** Ingestion runs in memory, so jobs interrupted by a restart are picked up again on startup. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterrupted() {
        List<UserDocument> pending = docs.findByStatusIn(List.of(DocumentStatus.STORED, DocumentStatus.PROCESSING));
        if (pending.isEmpty()) return;
        log.info("Resuming ingestion for {} interrupted document(s)", pending.size());
        pending.forEach(d -> processor.ingest(d.getId()));
    }
}
