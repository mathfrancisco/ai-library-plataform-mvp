package com.ailibrary.rag;

import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.document.DocumentService;
import com.ailibrary.document.domain.DocumentStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RagService {
    static final int TOP_K = 6;
    static final double SIMILARITY_THRESHOLD = 0.60;

    private final DocumentService documents;
    private final GroundedAnswerService grounded;

    public RagService(DocumentService documents, GroundedAnswerService grounded) {
        this.documents = documents;
        this.grounded = grounded;
    }

    public RagAnswer ask(UUID owner, UUID documentId, String question) {
        // Relational ownership check first; the vector filter below enforces it again.
        var doc = documents.owned(owner, documentId);
        if (doc.getStatus() != DocumentStatus.READY)
            throw new BadRequestException("DOCUMENT_NOT_READY", "Document is not ready for RAG");
        return grounded.answer(
                owner,
                "DOCUMENT_RAG",
                question,
                new GroundedAnswerService.Retrieval(
                        VectorFilters.documentChunks(owner, documentId), TOP_K, SIMILARITY_THRESHOLD),
                doc.getOriginalName(),
                "I could not find enough relevant context in this document.");
    }
}
