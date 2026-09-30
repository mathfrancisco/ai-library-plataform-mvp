package com.ailibrary.admin;

import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookVectorIndexer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operational endpoints; /api/admin/** requires the ADMIN role (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final BookVectorIndexer indexer;
    private final BookRepository books;

    public AdminController(BookVectorIndexer indexer, BookRepository books) {
        this.indexer = indexer;
        this.books = books;
    }

    public record ReindexResult(int indexed) {}

    @PostMapping("/books/reindex")
    public ReindexResult reindexBooks() {
        return new ReindexResult(indexer.reindexAll());
    }
}
