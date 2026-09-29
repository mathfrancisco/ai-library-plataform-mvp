package com.ailibrary.rag;
import com.ailibrary.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/books")
public class BookRagController {
    private final BookRagService rag; private final CurrentUser currentUser;
    public BookRagController(BookRagService rag,CurrentUser currentUser){this.rag=rag;this.currentUser=currentUser;}
    public record AskRequest(@NotBlank String question){}
    @PostMapping("/{bookId}/chat") public RagService.RagAnswer ask(@PathVariable UUID bookId,@Valid @RequestBody AskRequest request){return rag.ask(currentUser.id(),bookId,request.question());}
}
