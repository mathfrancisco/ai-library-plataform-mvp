package com.ailibrary.ai;

import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemView;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tools are instantiated per request and bound to the authenticated user. The LLM never supplies
 * a user id; every call goes through the same owner-scoped services as the REST API.
 */
public class LibraryAssistantTools {
    private final UUID userId;
    private final LibraryService library;
    private final ReadingProgressService reading;

    public LibraryAssistantTools(UUID userId, LibraryService library, ReadingProgressService reading) {
        this.userId = userId;
        this.library = library;
        this.reading = reading;
    }

    @Tool(description = "List books from the authenticated user's library, optionally filtered by status")
    public List<LibraryItemView> getMyBooksByStatus(
            @ToolParam(required = false, description = "One of WANT_TO_READ, READING, READ, DROPPED; empty for all")
                    String status) {
        return library.list(userId, status == null || status.isBlank() ? null : parseStatus(status));
    }

    @Tool(description = "List books the authenticated user is currently reading")
    public List<LibraryItemView> getCurrentlyReading() {
        return library.list(userId, LibraryStatus.READING);
    }

    @Tool(description = "Get the authenticated user's reading progress for a local book id")
    public ProgressView getReadingProgress(@ToolParam(description = "Local book UUID") String bookId) {
        return reading.get(userId, parseId(bookId));
    }

    @Tool(description = "Add an existing local book to the authenticated user's library with a reading status")
    public LibraryItemView addLocalBookToLibrary(
            @ToolParam(description = "Local book UUID") String bookId,
            @ToolParam(description = "One of WANT_TO_READ, READING, READ, DROPPED") String status) {
        return library.upsert(userId, parseId(bookId), new UpsertRequest(parseStatus(status), null, null));
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
            throw new IllegalArgumentException("bookId must be a local book UUID from the user's library");
        }
    }
}
