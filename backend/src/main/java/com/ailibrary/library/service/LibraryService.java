package com.ailibrary.library.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.dto.LibraryDtos.BookSummary;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemSummary;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemView;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.repository.UserLibraryRepository;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.service.ReadingProgressService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's shelf, and the single home of the rules linking shelf status and reading progress (SPEC-04 §5.3):
 * <ul>
 *   <li>status → READING sets startedAt when empty;</li>
 *   <li>status → READ sets completedAt, 100% and the last page;</li>
 *   <li>the first progress save moves WANT_TO_READ (or a book not yet shelved) to READING;</li>
 *   <li>progress reaching 100% moves the book to READ.</li>
 * </ul>
 */
@Service
public class LibraryService {
    private final UserLibraryRepository library;
    private final BookRepository books;
    private final ReadingProgressService reading;
    private final Clock clock;

    @Autowired
    public LibraryService(UserLibraryRepository library, BookRepository books, ReadingProgressService reading) {
        this(library, books, reading, Clock.systemUTC());
    }

    LibraryService(UserLibraryRepository library, BookRepository books, ReadingProgressService reading, Clock clock) {
        this.library = library;
        this.books = books;
        this.reading = reading;
        this.clock = clock;
    }

    /** POST semantics: adds the book, or returns the existing item unchanged (idempotent). */
    @Transactional
    public LibraryItemView add(UUID userId, UUID bookId, UpsertRequest request) {
        Book book = book(bookId);
        Optional<UserLibraryItem> existing = library.findByUserIdAndBookId(userId, bookId);
        if (existing.isPresent()) return toView(existing.get(), book);
        UserLibraryItem item = new UserLibraryItem(
                userId, bookId, request.status() == null ? LibraryStatus.WANT_TO_READ : request.status());
        item.update(null, request.favorite(), request.rating());
        item = library.save(item);
        applyStatusRules(userId, book, item.getStatus());
        return toView(item, book);
    }

    /** PATCH semantics: updates an item already on the shelf; 404 otherwise. */
    @Transactional
    public LibraryItemView update(UUID userId, UUID bookId, UpsertRequest request) {
        Book book = book(bookId);
        UserLibraryItem item = owned(userId, bookId);
        LibraryStatus previous = item.getStatus();
        item.update(request.status(), request.favorite(), request.rating());
        item = library.save(item);
        if (item.getStatus() != previous) applyStatusRules(userId, book, item.getStatus());
        return toView(item, book);
    }

    /** Used by the assistant tool: add or change status in one step. */
    @Transactional
    public LibraryItemView addOrUpdate(UUID userId, UUID bookId, UpsertRequest request) {
        return library.findByUserIdAndBookId(userId, bookId).isPresent()
                ? update(userId, bookId, request)
                : add(userId, bookId, request);
    }

    @Transactional
    public ProgressView saveProgress(UUID userId, UUID bookId, UpdateRequest request) {
        Book book = book(bookId);
        ProgressView saved = reading.save(userId, book, request);
        UserLibraryItem item = library.findByUserIdAndBookId(userId, bookId).orElse(null);
        boolean finished =
                saved.percentage() != null && saved.percentage().compareTo(ReadingProgressService.HUNDRED) >= 0;
        LibraryStatus target = finished ? LibraryStatus.READ : LibraryStatus.READING;
        if (item == null) {
            item = library.save(new UserLibraryItem(userId, bookId, target));
            applyStatusRules(userId, book, target);
        } else if (finished && item.getStatus() != LibraryStatus.READ
                || item.getStatus() == LibraryStatus.WANT_TO_READ) {
            item.update(target, null, null);
            library.save(item);
            applyStatusRules(userId, book, target);
        }
        return reading.get(userId, bookId);
    }

    @Transactional(readOnly = true)
    public LibraryItemView get(UUID userId, UUID bookId) {
        return toView(owned(userId, bookId), book(bookId));
    }

    @Transactional(readOnly = true)
    public List<LibraryItemSummary> list(UUID userId, LibraryStatus status) {
        List<UserLibraryItem> items = status == null
                ? library.findByUserIdOrderByAddedAtDesc(userId)
                : library.findByUserIdAndStatusOrderByAddedAtDesc(userId, status);
        List<UUID> ids = items.stream().map(UserLibraryItem::getBookId).toList();
        Map<UUID, Book> byId = new HashMap<>();
        books.findAllById(ids).forEach(b -> byId.put(b.getId(), b));
        Map<UUID, ProgressView> progress = reading.forBooks(userId, ids);
        return items.stream()
                .filter(i -> byId.containsKey(i.getBookId()))
                .map(i -> summary(i, byId.get(i.getBookId()), progress.get(i.getBookId())))
                .toList();
    }

    /** Removing a book also removes its reading progress (SPEC-04 §5.6). */
    @Transactional
    public void remove(UUID userId, UUID bookId) {
        library.delete(owned(userId, bookId));
        reading.delete(userId, bookId);
    }

    private void applyStatusRules(UUID userId, Book book, LibraryStatus status) {
        LocalDate today = LocalDate.now(clock);
        switch (status) {
            case READING -> reading.apply(userId, book.getId(), null, null, today, null);
            case READ -> {
                Integer lastPage = book.getPageCount() != null && book.getPageCount() > 0 ? book.getPageCount() : null;
                reading.apply(userId, book.getId(), lastPage, ReadingProgressService.HUNDRED, today, today);
            }
            default -> {}
        }
    }

    private UserLibraryItem owned(UUID userId, UUID bookId) {
        return library.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.LIBRARY_ITEM_NOT_FOUND, "Book is not in your library"));
    }

    private Book book(UUID bookId) {
        return books.findById(bookId).orElseThrow(NotFoundException::book);
    }

    private LibraryItemView toView(UserLibraryItem item, Book book) {
        return new LibraryItemView(
                BookMapper.toView(book),
                item.getStatus(),
                item.isFavorite(),
                item.getRating(),
                item.getAddedAt(),
                item.getUpdatedAt(),
                reading.get(item.getUserId(), book.getId()));
    }

    public static BookSummary summary(Book book) {
        BookView v = BookMapper.toView(book);
        return new BookSummary(
                v.id(),
                v.title(),
                v.subtitle(),
                v.authors(),
                v.categories(),
                v.coverUrl(),
                v.publishedYear(),
                v.pageCount());
    }

    private static LibraryItemSummary summary(UserLibraryItem item, Book book, ProgressView progress) {
        return new LibraryItemSummary(
                summary(book),
                item.getStatus(),
                item.isFavorite(),
                item.getRating(),
                item.getAddedAt(),
                item.getUpdatedAt(),
                progress == null ? BigDecimal.ZERO : progress.percentage());
    }
}
