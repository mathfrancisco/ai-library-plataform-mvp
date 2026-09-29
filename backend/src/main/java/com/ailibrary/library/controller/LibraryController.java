package com.ailibrary.library.controller;

import com.ailibrary.common.security.CurrentUser;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemView;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/library")
public class LibraryController {
    private final LibraryService library;
    private final CurrentUser currentUser;

    public LibraryController(LibraryService library, CurrentUser currentUser) {
        this.library = library;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<LibraryItemView> list(@RequestParam(required = false) LibraryStatus status) {
        return library.list(currentUser.id(), status);
    }

    @PostMapping("/books/{bookId}")
    public LibraryItemView add(@PathVariable UUID bookId, @Valid @RequestBody UpsertRequest request) {
        return library.upsert(currentUser.id(), bookId, request);
    }

    @PatchMapping("/books/{bookId}")
    public LibraryItemView update(@PathVariable UUID bookId, @Valid @RequestBody UpsertRequest request) {
        return library.upsert(currentUser.id(), bookId, request);
    }

    @DeleteMapping("/books/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID bookId) {
        library.remove(currentUser.id(), bookId);
    }
}
