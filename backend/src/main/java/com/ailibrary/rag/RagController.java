package com.ailibrary.rag;
import com.ailibrary.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/documents")
public class RagController {
 private final RagService rag; private final CurrentUser currentUser;
 public RagController(RagService rag,CurrentUser currentUser){this.rag=rag;this.currentUser=currentUser;}
 public record AskRequest(@NotBlank String question){}
 @PostMapping("/{id}/chat") public RagService.RagAnswer chat(@PathVariable UUID id,@Valid @RequestBody AskRequest request){return rag.ask(currentUser.id(),id,request.question());}
}
