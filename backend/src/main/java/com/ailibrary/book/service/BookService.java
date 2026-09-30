package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.dto.CreateBookRequest;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.error.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return books.findById(id).orElseThrow(NotFoundException::book);
    }

    @Transactional
    public BookView create(CreateBookRequest request) {
        String isbn13 = BookFingerprint.isbn13(request.isbn13());
        if (isbn13 == null) isbn13 = BookFingerprint.isbn10To13(request.isbn10());
        if (isbn13 != null && books.findByIsbn13(isbn13).isPresent())
            throw new ApiException(ErrorCode.BOOK_ALREADY_EXISTS, "A book with this ISBN already exists");
        Book entity = new Book(
                isbn13,
                BookFingerprint.isbn10(request.isbn10()),
                request.title().trim(),
                request.subtitle(),
                request.authorNames(),
                request.categoryNames(),
                request.description(),
                request.language(),
                request.publisher(),
                request.publishedYear(),
                request.pageCount(),
                request.coverUrl(),
                Boolean.TRUE.equals(request.publicDomain()));
        entity = books.save(entity);
        vectorIndexer.index(entity);
        return BookMapper.toView(entity);
    }

    @Transactional(readOnly = true)
    public BookView get(UUID id) {
        return BookMapper.toView(getEntity(id));
    }
}
