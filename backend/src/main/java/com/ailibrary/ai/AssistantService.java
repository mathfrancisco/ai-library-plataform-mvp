package com.ailibrary.ai;

import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.service.ReadingProgressService;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AssistantService {
    private final AiFacade ai; private final LibraryService library; private final ReadingProgressService reading;
    public AssistantService(AiFacade ai, LibraryService library, ReadingProgressService reading){ this.ai=ai; this.library=library; this.reading=reading; }
    public String ask(UUID userId, String message){
        var tools = new LibraryAssistantTools(userId, library, reading);
        return ai.tools(userId,"LIBRARY_ASSISTANT", "You are a personal library assistant. Use tools for factual claims about the user's library. Never assume ownership or invent book state. Ask no destructive tool actions because none are exposed.", message, tools);
    }
}
