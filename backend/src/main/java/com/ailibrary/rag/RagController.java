package com.ailibrary.rag;

import com.ailibrary.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RagController {
    private final RagService documentRag;
    private final BookRagService bookRag;
    private final CurrentUser currentUser;

    public RagController(RagService documentRag, BookRagService bookRag, CurrentUser currentUser) {
        this.documentRag = documentRag;
        this.bookRag = bookRag;
        this.currentUser = currentUser;
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String question) {}

    @PostMapping("/documents/{id}/chat")
    public RagAnswer chatDocument(@PathVariable UUID id, @Valid @RequestBody AskRequest request) {
        return documentRag.ask(currentUser.id(), id, request.question());
    }

    @PostMapping("/books/{bookId}/chat")
    public RagAnswer chatBook(@PathVariable UUID bookId, @Valid @RequestBody AskRequest request) {
        return bookRag.ask(currentUser.id(), bookId, request.question());
    }
}
