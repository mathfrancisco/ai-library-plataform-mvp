package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.dto.CreateBookRequest;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.error.NotFoundException;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {
    private final BookRepository books;
    private final BookDeduplicator deduplicator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public BookService(BookRepository books, BookDeduplicator deduplicator, ApplicationEventPublisher events) {
        this(books, deduplicator, events, Clock.systemUTC());
    }

    BookService(BookRepository books, BookDeduplicator deduplicator, ApplicationEventPublisher events, Clock clock) {
        this.books = books;
        this.deduplicator = deduplicator;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Book getEntity(UUID id) {
        return books.findById(id).orElseThrow(NotFoundException::book);
    }

    public record Created(BookView book, boolean created) {}

    /**
     * Registers a book, or returns the existing local record when the ISBN or normalized title + first author
     * already exists, so the shared catalog does not collect duplicates.
     */
    @Transactional
    public Created create(CreateBookRequest request) {
        int maxYear = Year.now(clock).getValue() + 1;
        if (request.publishedYear() != null && request.publishedYear() > maxYear)
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "publishedYear: must be at most " + maxYear);
        List<String> authors = clean(request.authors());
        Optional<Book> existing =
                deduplicator.findExisting(request.isbn13(), request.isbn10(), request.title(), authors);
        if (existing.isPresent()) return new Created(BookMapper.toView(existing.get()), false);
        String isbn13 = BookFingerprint.isbn13(request.isbn13());
        if (isbn13 == null) isbn13 = BookFingerprint.isbn10To13(request.isbn10());
        Book entity = books.save(new Book(
                isbn13,
                BookFingerprint.isbn10(request.isbn10()),
                request.title().trim(),
                blankToNull(request.subtitle()),
                BookMapper.join(authors),
                BookMapper.join(clean(request.categories())),
                blankToNull(request.description()),
                blankToNull(request.language()),
                blankToNull(request.publisher()),
                request.publishedYear(),
                request.pageCount(),
                blankToNull(request.coverUrl()),
                Boolean.TRUE.equals(request.publicDomain())));
        events.publishEvent(new BookSavedEvent(entity.getId()));
        return new Created(BookMapper.toView(entity), true);
    }

    @Transactional(readOnly = true)
    public BookView get(UUID id) {
        return BookMapper.toView(getEntity(id));
    }

    private static List<String> clean(List<String> values) {
        return values == null
                ? List.of()
                : values.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
