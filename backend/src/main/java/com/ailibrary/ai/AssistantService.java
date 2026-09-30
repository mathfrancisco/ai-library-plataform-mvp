package com.ailibrary.ai;

import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
    static final String SYSTEM_PROMPT =
            """
            You are a personal library assistant.
            Use the provided tools for any factual claim about the user's library or reading progress.
            Never assume ownership or invent book state. Book ids are local UUIDs returned by the tools.
            You can add an existing local book to the library; you cannot delete anything.
            If a request needs an action you do not have a tool for, explain what the user can do in the app instead.
            """;

    private final AiFacade ai;
    private final LibraryService library;
    private final ReadingProgressService reading;

    public AssistantService(AiFacade ai, LibraryService library, ReadingProgressService reading) {
        this.ai = ai;
        this.library = library;
        this.reading = reading;
    }

    public String ask(UUID userId, String message) {
        var tools = new LibraryAssistantTools(userId, library, reading);
        return ai.tools(userId, "LIBRARY_ASSISTANT", SYSTEM_PROMPT, message, tools);
    }
}
