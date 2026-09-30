package com.ailibrary.ai;

import com.ailibrary.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AiController {
    private final AssistantService assistant; private final BookSummaryService summaries; private final CurrentUser currentUser; private final AiUsageService usage;
    public AiController(AssistantService assistant, BookSummaryService summaries, CurrentUser currentUser, AiUsageService usage){ this.assistant=assistant; this.summaries=summaries; this.currentUser=currentUser; this.usage=usage; }
    public record ChatRequest(@NotBlank @Size(max = 2000) String message) {}
    public record ChatResponse(String answer) {}
    @GetMapping("/ai/usage") public AiUsageService.Usage usage(){ return usage.forUser(currentUser.id()); }
    @PostMapping("/ai/assistant") public ChatResponse assistant(@Valid @RequestBody ChatRequest request){ return new ChatResponse(assistant.ask(currentUser.id(), request.message())); }
    @PostMapping("/books/{bookId}/summary") public ChatResponse summary(@PathVariable UUID bookId,@RequestParam(defaultValue="SHORT") BookSummaryService.SummaryType type){ return new ChatResponse(summaries.summarize(currentUser.id(),bookId,type)); }
}
