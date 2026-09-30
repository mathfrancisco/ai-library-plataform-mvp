package com.ailibrary.reading.controller;

import com.ailibrary.common.security.CurrentUser;
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
    private final CurrentUser currentUser;

    public ReadingController(ReadingProgressService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/{bookId}")
    public ProgressView get(@PathVariable UUID bookId) {
        return service.get(currentUser.id(), bookId);
    }

    @PutMapping("/{bookId}")
    public ProgressView update(@PathVariable UUID bookId, @Valid @RequestBody UpdateRequest request) {
        return service.upsert(currentUser.id(), bookId, request);
    }
}
