package com.ailibrary.ai;

import com.ailibrary.ai.domain.AiGeneration;
import com.ailibrary.ai.repository.AiGenerationRepository;
import com.ailibrary.book.domain.Book;
import com.ailibrary.book.service.BookService;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class BookSummaryService {
    public enum SummaryType {
        TLDR,
        SHORT,
        TAKEAWAYS
    }

    static final String OPERATION = "book-summary";
    static final String PROMPT_VERSION = "v1";

    private final BookService books;
    private final AiFacade ai;
    private final AiGenerationRepository cache;

    public BookSummaryService(BookService books, AiFacade ai, AiGenerationRepository cache) {
        this.books = books;
        this.ai = ai;
        this.cache = cache;
    }

    public String summarize(UUID userId, UUID bookId, SummaryType type) {
        Book book = books.getEntity(bookId);
        if (book.getDescription() == null || book.getDescription().isBlank())
            throw new BadRequestException(
                    ErrorCode.NO_SUMMARY_SOURCE,
                    "This book has no source description to summarize. Upload permitted full text for grounded RAG.");
        String source =
                book.getTitle() + "\n" + Objects.toString(book.getAuthorNames(), "") + "\n" + book.getDescription();
        String model = ai.properties().model(ModelTier.FAST);
        String hash = cacheKey(bookId, source, model, type);
        var cached = cache.findByPromptHash(hash);
        if (cached.isPresent()) return cached.get().getResult();

        String result = ai.complete(
                userId,
                "BOOK_SUMMARY",
                ModelTier.FAST,
                AiPromptTemplates.SUMMARY_SYSTEM + AiPromptTemplates.summaryInstruction(type),
                source);
        try {
            cache.save(new AiGeneration(
                    userId,
                    "BOOK",
                    bookId,
                    "SUMMARY_" + type,
                    hash,
                    ai.properties().provider(),
                    model,
                    result));
        } catch (DataIntegrityViolationException concurrentInsert) {
            return cache.findByPromptHash(hash).map(AiGeneration::getResult).orElse(result);
        }
        return result;
    }

    /** SHA-256 of operation + entityId + sourceVersion + model + normalized prompt parameters (docs/06-spring-ai-rag.md). */
    static String cacheKey(UUID bookId, String source, String model, SummaryType type) {
        String sourceVersion = sha256(source.strip().replaceAll("\\s+", " "));
        return sha256(String.join(
                "|",
                OPERATION,
                bookId.toString(),
                sourceVersion,
                Objects.toString(model, ""),
                PROMPT_VERSION,
                "type=" + type));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
