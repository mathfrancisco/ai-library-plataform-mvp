package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.dto.CreateBookRequest;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BookService {
    private final BookRepository books;
    private final BookVectorIndexer vectorIndexer;

    public BookService(BookRepository books, BookVectorIndexer vectorIndexer) {
        this.books = books;
        this.vectorIndexer = vectorIndexer;
    }

    @Transactional(readOnly = true)
    public Book getEntity(UUID id) {
        return books.findById(id).orElseThrow(() -> new NotFoundException("Book not found"));
    }

    @Transactional
    public BookView create(CreateBookRequest request) {
        Book entity = new Book(request.isbn13(), request.isbn10(), request.title(), request.subtitle(), request.authorNames(),
                request.categoryNames(), request.description(), request.language(), request.publisher(), request.publishedYear(),
                request.pageCount(), request.coverUrl(), request.publicDomain());
        entity = books.save(entity);
        vectorIndexer.index(entity);
        return BookMapper.toView(entity);
    }

    @Transactional(readOnly = true)
    public BookView get(UUID id) {
        return BookMapper.toView(getEntity(id));
    }
}
