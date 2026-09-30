package com.ailibrary.document;

import static org.mockito.Mockito.*;

import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentIngestionWorkerTest {
    @Test
    void resumesDocumentsInterruptedByARestart() {
        DocumentIngestionProcessor processor = mock(DocumentIngestionProcessor.class);
        UserDocumentRepository docs = mock(UserDocumentRepository.class);
        UserDocument stored = new UserDocument(UUID.randomUUID(), null, "a.md", "text/markdown", 1, "k1");
        UserDocument processing = new UserDocument(UUID.randomUUID(), null, "b.md", "text/markdown", 1, "k2");
        when(docs.findByStatusIn(List.of(DocumentStatus.STORED, DocumentStatus.PROCESSING)))
                .thenReturn(List.of(stored, processing));

        new DocumentIngestionWorker(processor, docs).resumeInterrupted();

        verify(processor).ingest(stored.getId());
        verify(processor).ingest(processing.getId());
    }
}
