package com.ailibrary.ai;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/** Stateless multi-turn assistant: the client sends recent turns; nothing is stored server-side (MVP). */
@Service
public class AssistantService {
    static final int MAX_TURNS = 10;
    static final int MAX_HISTORY_CHARS = 4000;

    private final AiFacade ai;
    private final LibraryService library;
    private final ReadingProgressService reading;
    private final BookRepository books;

    public AssistantService(AiFacade ai, LibraryService library, ReadingProgressService reading, BookRepository books) {
        this.ai = ai;
        this.library = library;
        this.reading = reading;
        this.books = books;
    }

    public enum Role {
        USER,
        ASSISTANT
    }

    public record Turn(Role role, String text) {}

    public String ask(UUID userId, String message, List<Turn> history) {
        var tools = new LibraryAssistantTools(userId, library, reading, books);
        return ai.tools(
                userId,
                "LIBRARY_ASSISTANT",
                ModelTier.SMART,
                AiPromptTemplates.ASSISTANT_SYSTEM,
                toMessages(history),
                message,
                tools);
    }

    /** Keeps the most recent turns within MAX_TURNS and MAX_HISTORY_CHARS, oldest first. */
    static List<Message> toMessages(List<Turn> history) {
        if (history == null || history.isEmpty()) return List.of();
        List<Message> kept = new ArrayList<>();
        int chars = 0;
        for (int i = history.size() - 1; i >= 0 && kept.size() < MAX_TURNS; i--) {
            Turn t = history.get(i);
            if (t == null || t.text() == null || t.text().isBlank() || t.role() == null) continue;
            if (chars + t.text().length() > MAX_HISTORY_CHARS) break;
            chars += t.text().length();
            kept.addFirst(t.role() == Role.USER ? new UserMessage(t.text()) : new AssistantMessage(t.text()));
        }
        return kept;
    }
}
