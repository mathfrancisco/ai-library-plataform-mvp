package com.ailibrary.library.controller;

import com.ailibrary.common.security.CurrentUser;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemSummary;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemView;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.service.LibraryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
    public List<LibraryItemSummary> list(@RequestParam(required = false) LibraryStatus status) {
        return library.list(currentUser.id(), status);
    }

    @GetMapping("/books/{bookId}")
    public LibraryItemView get(@PathVariable UUID bookId) {
        return library.get(currentUser.id(), bookId);
    }

    /** Adds the book; if it is already on the shelf the existing item is returned unchanged. */
    @PostMapping("/books/{bookId}")
    public LibraryItemView add(@PathVariable UUID bookId, @Valid @RequestBody UpsertRequest request) {
        return library.add(currentUser.id(), bookId, request);
    }

    /** Updates an item on the shelf; 404 LIBRARY_ITEM_NOT_FOUND when the book is not there. */
    @PatchMapping("/books/{bookId}")
    public LibraryItemView update(@PathVariable UUID bookId, @Valid @RequestBody UpsertRequest request) {
        return library.update(currentUser.id(), bookId, request);
    }

    @DeleteMapping("/books/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID bookId) {
        library.remove(currentUser.id(), bookId);
    }
}
