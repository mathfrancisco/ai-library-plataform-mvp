package com.ailibrary.document;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentIngestionWorker {
    private final DocumentIngestionProcessor processor;
    public DocumentIngestionWorker(DocumentIngestionProcessor processor){this.processor=processor;}

    @Async
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void onUpload(DocumentUploadedEvent event){processor.ingest(event.documentId());}
}
