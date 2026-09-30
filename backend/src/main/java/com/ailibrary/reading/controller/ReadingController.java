package com.ailibrary.reading.controller;

import com.ailibrary.common.security.CurrentUser;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.service.ReadingProgressService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reading")
public class ReadingController {
    private final ReadingProgressService service;
    private final LibraryService library;
    private final CurrentUser currentUser;

    public ReadingController(ReadingProgressService service, LibraryService library, CurrentUser currentUser) {
        this.service = service;
        this.library = library;
        this.currentUser = currentUser;
    }

    @GetMapping("/{bookId}")
    public ProgressView get(@PathVariable UUID bookId) {
        return service.get(currentUser.id(), bookId);
    }

    @PutMapping("/{bookId}")
    public ProgressView update(@PathVariable UUID bookId, @Valid @RequestBody UpdateRequest request) {
        // Saving goes through LibraryService so progress/status rules apply in one transaction.
        return library.saveProgress(currentUser.id(), bookId, request);
    }
}
