package com.ailibrary.ai;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemSummary;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.service.ReadingProgressService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tools are instantiated per request and bound to the authenticated user. The LLM never supplies a user id;
 * every call goes through the same owner-scoped services as the REST API. Tools return compact records and
 * short error strings instead of throwing, so bad model input becomes a message the model can correct.
 */
public class LibraryAssistantTools {
    static final int MAX_CALLS = 5;

    private final UUID userId;
    private final LibraryService library;
    private final ReadingProgressService reading;
    private final BookRepository books;
    private final AtomicInteger calls = new AtomicInteger();

    public LibraryAssistantTools(
            UUID userId, LibraryService library, ReadingProgressService reading, BookRepository books) {
        this.userId = userId;
        this.library = library;
        this.reading = reading;
        this.books = books;
    }

    public record ShelfBook(
            String bookId, String title, List<String> authors, String status, BigDecimal progressPercent) {}

    public record FoundBook(String bookId, String title, List<String> authors, Integer publishedYear) {}

    public record ToolResult<T>(T data, String error) {
        static <T> ToolResult<T> ok(T data) {
            return new ToolResult<>(data, null);
        }

        static <T> ToolResult<T> fail(String error) {
            return new ToolResult<>(null, error);
        }
    }

    @Tool(description = "List books from the authenticated user's library, optionally filtered by status")
    public ToolResult<List<ShelfBook>> getMyBooksByStatus(
            @ToolParam(required = false, description = "One of WANT_TO_READ, READING, READ, DROPPED; empty for all")
                    String status) {
        return guarded(() -> shelf(status == null || status.isBlank() ? null : parseStatus(status)));
    }

    @Tool(description = "List books the authenticated user is currently reading, with progress")
    public ToolResult<List<ShelfBook>> getCurrentlyReading() {
        return guarded(() -> shelf(LibraryStatus.READING));
    }

    @Tool(description = "Get the authenticated user's reading progress for a local book id")
    public ToolResult<ProgressView> getReadingProgress(@ToolParam(description = "Local book UUID") String bookId) {
        return guarded(() -> reading.get(userId, parseId(bookId)));
    }

    @Tool(description = "Find local books by title, author or topic. Returns ids usable with the other tools")
    public ToolResult<List<FoundBook>> findLocalBooks(@ToolParam(description = "Search words") String query) {
        return guarded(() -> {
            if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be empty");
            return books.lexicalSearch(query.trim(), 5).stream()
                    .map(b -> new FoundBook(
                            b.getId().toString(),
                            b.getTitle(),
                            BookMapper.toView(b).authors(),
                            b.getPublishedYear()))
                    .toList();
        });
    }

    @Tool(description = "Add an existing local book to the authenticated user's library, or change its status")
    public ToolResult<ShelfBook> addLocalBookToLibrary(
            @ToolParam(description = "Local book UUID") String bookId,
            @ToolParam(description = "One of WANT_TO_READ, READING, READ, DROPPED") String status) {
        return guarded(() -> {
            var item = library.addOrUpdate(userId, parseId(bookId), new UpsertRequest(parseStatus(status), null, null));
            return new ShelfBook(
                    item.book().id().toString(),
                    item.book().title(),
                    item.book().authors(),
                    item.status().name(),
                    item.progress().percentage());
        });
    }

    private List<ShelfBook> shelf(LibraryStatus status) {
        return library.list(userId, status).stream()
                .map(LibraryAssistantTools::brief)
                .toList();
    }

    private static ShelfBook brief(LibraryItemSummary i) {
        return new ShelfBook(
                i.book().id().toString(),
                i.book().title(),
                i.book().authors(),
                i.status().name(),
                i.percentage());
    }

    /** Caps tool iterations per question (SPEC-04 §12.9g) and turns failures into model-readable errors. */
    private <T> ToolResult<T> guarded(java.util.function.Supplier<T> action) {
        if (calls.incrementAndGet() > MAX_CALLS) {
            return ToolResult.fail("Tool call limit reached for this question; answer with what you have.");
        }
        try {
            return ToolResult.ok(action.get());
        } catch (IllegalArgumentException | ApiException ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }

    static LibraryStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return LibraryStatus.WANT_TO_READ;
        String normalized =
                raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return LibraryStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown status '" + raw + "'. Use WANT_TO_READ, READING, READ or DROPPED.");
        }
    }

    private static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "bookId must be a local book UUID; use findLocalBooks to look it up by title");
        }
    }
}
