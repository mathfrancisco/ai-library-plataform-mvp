package com.ailibrary.library.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.dto.LibraryDtos.LibraryItemView;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.repository.UserLibraryRepository;
import com.ailibrary.reading.service.ReadingProgressService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryService {
    private final UserLibraryRepository library;
    private final BookRepository books;
    private final ReadingProgressService reading;

    public LibraryService(UserLibraryRepository library, BookRepository books, ReadingProgressService reading) {
        this.library = library;
        this.books = books;
        this.reading = reading;
    }

    @Transactional
    public LibraryItemView upsert(UUID userId, UUID bookId, UpsertRequest request) {
        Book book = books.findById(bookId).orElseThrow(NotFoundException::book);
        Optional<UserLibraryItem> existing = library.findByUserIdAndBookId(userId, bookId);
        LibraryStatus previous = existing.map(UserLibraryItem::getStatus).orElse(null);
        UserLibraryItem item = existing.orElseGet(() -> new UserLibraryItem(
                userId, bookId, request.status() == null ? LibraryStatus.WANT_TO_READ : request.status()));
        item.update(request.status(), request.favorite(), request.rating());
        item = library.save(item);
        if (item.getStatus() != previous) reading.onStatusChanged(userId, bookId, item.getStatus());
        return toView(item, book);
    }

    @Transactional(readOnly = true)
    public List<LibraryItemView> list(UUID userId, LibraryStatus status) {
        List<UserLibraryItem> items = status == null
                ? library.findByUserIdOrderByAddedAtDesc(userId)
                : library.findByUserIdAndStatusOrderByAddedAtDesc(userId, status);
        Map<UUID, Book> byId = new HashMap<>();
        books.findAllById(items.stream().map(UserLibraryItem::getBookId).toList())
                .forEach(b -> byId.put(b.getId(), b));
        return items.stream()
                .filter(i -> byId.containsKey(i.getBookId()))
                .map(i -> toView(i, byId.get(i.getBookId())))
                .toList();
    }

    @Transactional
    public void remove(UUID userId, UUID bookId) {
        UserLibraryItem item = library.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new NotFoundException("LIBRARY_ITEM_NOT_FOUND", "Library item not found"));
        library.delete(item);
    }

    private LibraryItemView toView(UserLibraryItem item, Book book) {
        return new LibraryItemView(
                BookMapper.toView(book), item.getStatus(), item.isFavorite(), item.getRating(), item.getAddedAt());
    }
}
