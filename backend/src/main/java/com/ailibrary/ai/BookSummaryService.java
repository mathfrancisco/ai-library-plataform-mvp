package com.ailibrary.ai;

import com.ailibrary.ai.domain.AiGeneration;
import com.ailibrary.ai.repository.AiGenerationRepository;
import com.ailibrary.book.domain.Book;
import com.ailibrary.book.service.BookService;
import com.ailibrary.common.error.BadRequestException;
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
        FULL,
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
                    "NO_SUMMARY_SOURCE",
                    "This book has no source description to summarize. Upload permitted full text for grounded RAG.");
        String source =
                book.getTitle() + "\n" + Objects.toString(book.getAuthorNames(), "") + "\n" + book.getDescription();
        String hash = cacheKey(bookId, source, ai.properties().model(), type);
        var cached = cache.findByPromptHash(hash);
        if (cached.isPresent()) return cached.get().getResult();

        String result = ai.complete(
                userId,
                "BOOK_SUMMARY",
                "You summarize only the supplied source. Never invent book contents beyond it. "
                        + "Clearly state that this is based on catalog metadata/description. " + instruction(type),
                source);
        try {
            cache.save(new AiGeneration(
                    userId,
                    "BOOK",
                    bookId,
                    "SUMMARY_" + type,
                    hash,
                    ai.properties().provider(),
                    ai.properties().model(),
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

    private static String instruction(SummaryType type) {
        return switch (type) {
            case TLDR -> "Return one compact paragraph.";
            case SHORT -> "Return 2-3 concise paragraphs.";
            case FULL -> "Return a detailed structured summary of the supplied catalog description only.";
            case TAKEAWAYS -> "Return 5-8 key takeaways supported by the supplied description.";
        };
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
