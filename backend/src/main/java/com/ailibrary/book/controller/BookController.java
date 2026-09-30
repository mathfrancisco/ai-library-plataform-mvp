package com.ailibrary.book.controller;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.dto.CreateBookRequest;
import com.ailibrary.book.service.BookService;
import com.ailibrary.book.service.SimilarBookService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService books;
    private final SimilarBookService similar;

    public BookController(BookService books, SimilarBookService similar) {
        this.books = books;
        this.similar = similar;
    }

    /** 201 with the new book, or 200 with the existing record when the book is already in the catalog. */
    @PostMapping
    public ResponseEntity<BookView> create(@Valid @RequestBody CreateBookRequest request) {
        BookService.Created result = books.create(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.book());
    }

    @GetMapping("/{id}/similar")
    public List<BookView> similar(@PathVariable UUID id, @RequestParam(defaultValue = "8") int limit) {
        return similar.similar(id, Math.max(1, Math.min(limit, 24)));
    }

    @GetMapping("/{id}")
    public BookView get(@PathVariable UUID id) {
        return books.get(id);
    }
}
