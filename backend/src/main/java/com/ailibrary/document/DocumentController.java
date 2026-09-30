package com.ailibrary.document;

import com.ailibrary.common.security.CurrentUser;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService service;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    public DocumentView upload(@RequestPart("file") MultipartFile file, @RequestParam(required = false) UUID bookId) {
        return service.upload(currentUser.id(), bookId, file);
    }

    @GetMapping
    public List<DocumentView> list() {
        return service.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public DocumentView get(@PathVariable UUID id) {
        return service.view(service.owned(currentUser.id(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(currentUser.id(), id);
    }
}
