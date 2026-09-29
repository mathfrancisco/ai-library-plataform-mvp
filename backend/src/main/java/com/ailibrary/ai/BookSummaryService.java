package com.ailibrary.ai;

import com.ailibrary.ai.domain.AiGeneration;
import com.ailibrary.ai.repository.AiGenerationRepository;
import com.ailibrary.book.domain.Book;
import com.ailibrary.book.service.BookService;
import com.ailibrary.common.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class BookSummaryService {
    public enum SummaryType { TLDR, SHORT, FULL, TAKEAWAYS }
    private final BookService books; private final AiFacade ai; private final AiGenerationRepository cache;
    public BookSummaryService(BookService books, AiFacade ai, AiGenerationRepository cache){ this.books=books; this.ai=ai; this.cache=cache; }

    @Transactional
    public String summarize(UUID userId, UUID bookId, SummaryType type) {
        Book book = books.getEntity(bookId);
        if (book.getDescription() == null || book.getDescription().isBlank())
            throw new BadRequestException("This book has no source description to summarize. Upload permitted full text for grounded RAG.");
        String source = book.getTitle() + "\n" + book.getAuthorNames() + "\n" + book.getDescription();
        String hash = sha256("book-summary|"+bookId+"|"+type+"|"+ai.properties().model()+"|"+source);
        return cache.findByPromptHash(hash).map(AiGeneration::getResult).orElseGet(() -> {
            String instruction = switch(type){
                case TLDR -> "Return one compact paragraph.";
                case SHORT -> "Return 2-3 concise paragraphs.";
                case FULL -> "Return a detailed structured summary of the supplied catalog description only.";
                case TAKEAWAYS -> "Return 5-8 key takeaways supported by the supplied description.";
            };
            String result = ai.complete(userId,"BOOK_SUMMARY", "You summarize only the supplied source. Never invent book contents beyond it. Clearly state that this is based on catalog metadata/description. "+instruction, source);
            cache.save(new AiGeneration(userId,"BOOK",bookId,"SUMMARY_"+type,hash,ai.properties().provider(),ai.properties().model(),result));
            return result;
        });
    }

    private String sha256(String value){ try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e);} }
}
