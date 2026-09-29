package com.ailibrary.book.controller;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.dto.CreateBookRequest;
import jakarta.validation.Valid;
import com.ailibrary.book.service.BookService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService books;
    private final com.ailibrary.book.service.SimilarBookService similar;

    public BookController(BookService books, com.ailibrary.book.service.SimilarBookService similar) {
        this.books = books;
        this.similar = similar;
    }

    @PostMapping
    public BookView create(@Valid @RequestBody CreateBookRequest request) {
        return books.create(request);
    }

    @GetMapping("/{id}/similar")
    public java.util.List<BookView> similar(@PathVariable UUID id, @RequestParam(defaultValue="8") int limit) {
        return similar.similar(id, Math.max(1, Math.min(limit, 24)));
    }

    @GetMapping("/{id}")
    public BookView get(@PathVariable UUID id) {
        return books.get(id);
    }
}
