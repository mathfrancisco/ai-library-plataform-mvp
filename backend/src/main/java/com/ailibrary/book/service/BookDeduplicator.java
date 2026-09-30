package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Finds an existing local book by ISBN-13, then ISBN-10, then normalized title + first author. */
@Component
public class BookDeduplicator {
    private final BookRepository books;

    public BookDeduplicator(BookRepository books) {
        this.books = books;
    }

    public Optional<Book> findExisting(String isbn13Raw, String isbn10Raw, String title, List<String> authors) {
        String isbn13 = BookFingerprint.isbn13(isbn13Raw);
        if (isbn13 == null) isbn13 = BookFingerprint.isbn10To13(isbn10Raw);
        if (isbn13 != null) {
            Optional<Book> byIsbn = books.findByIsbn13(isbn13);
            if (byIsbn.isPresent()) return byIsbn;
        }
        String isbn10 = BookFingerprint.isbn10(isbn10Raw);
        if (isbn10 != null) {
            Optional<Book> byIsbn10 = books.findByIsbn10(isbn10).stream().findFirst();
            if (byIsbn10.isPresent()) return byIsbn10;
        }
        if (title == null || title.isBlank()) return Optional.empty();
        String wanted = BookFingerprint.of(null, null, title, authors);
        return books.findTop20ByTitleKey(BookFingerprint.normalizeText(title)).stream()
                .filter(b -> BookFingerprint.of(
                                null, null, b.getTitle(), BookMapper.toView(b).authors())
                        .equals(wanted))
                .findFirst();
    }
}
