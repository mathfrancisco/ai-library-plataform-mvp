package com.ailibrary.rag;

import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.document.domain.DocumentStatus;
import com.ailibrary.document.repository.UserDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BookRagService {
    static final int TOP_K = 8;
    static final double SIMILARITY_THRESHOLD = 0.58;

    private final UserDocumentRepository docs;
    private final GroundedAnswerService grounded;

    public BookRagService(UserDocumentRepository docs, GroundedAnswerService grounded) {
        this.docs = docs;
        this.grounded = grounded;
    }

    public RagAnswer ask(UUID owner, UUID bookId, String question) {
        if (docs.findByOwnerIdAndBookIdAndStatus(owner, bookId, DocumentStatus.READY).isEmpty())
            throw new BadRequestException("NO_BOOK_DOCUMENTS", "Upload a permitted document for this book before using book chat");
        return grounded.answer(owner, "BOOK_RAG", question,
                new GroundedAnswerService.Retrieval(VectorFilters.bookChunks(owner, bookId), TOP_K, SIMILARITY_THRESHOLD),
                "book document", "I could not find enough relevant context in your uploaded sources for this book.");
    }
}
