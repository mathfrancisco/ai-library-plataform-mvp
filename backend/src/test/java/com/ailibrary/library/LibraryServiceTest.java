package com.ailibrary.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.dto.LibraryDtos.UpsertRequest;
import com.ailibrary.library.repository.UserLibraryRepository;
import com.ailibrary.library.service.LibraryService;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.service.ReadingProgressService;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LibraryServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private final UserLibraryRepository repo = mock(UserLibraryRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final ReadingProgressService reading = mock(ReadingProgressService.class);
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    private final Book book =
            new Book(null, null, "Dune", null, "Frank Herbert", null, null, null, null, null, 400, null, false);
    private LibraryService service;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<LibraryService> c = LibraryService.class.getDeclaredConstructor(
                UserLibraryRepository.class, BookRepository.class, ReadingProgressService.class, Clock.class);
        c.setAccessible(true);
        service = c.newInstance(
                repo, books, reading, Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(books.findById(book.getId())).thenReturn(Optional.of(book));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reading.get(any(), any())).thenReturn(ProgressView.empty(book.getId()));
    }

    @Test
    void addDefaultsToWantToReadAndIsIdempotent() {
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.empty());
        var view = service.add(alice, book.getId(), new UpsertRequest(null, true, 5));
        assertThat(view.status()).isEqualTo(LibraryStatus.WANT_TO_READ);
        assertThat(view.favorite()).isTrue();

        UserLibraryItem existing = new UserLibraryItem(alice, book.getId(), LibraryStatus.READ);
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.of(existing));
        assertThat(service.add(alice, book.getId(), new UpsertRequest(LibraryStatus.DROPPED, null, null))
                        .status())
                .isEqualTo(LibraryStatus.READ);
    }

    @Test
    void statusReadingSetsStartDateAndReadCompletesProgress() {
        UserLibraryItem item = new UserLibraryItem(alice, book.getId(), LibraryStatus.WANT_TO_READ);
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.of(item));
        service.update(alice, book.getId(), new UpsertRequest(LibraryStatus.READING, null, null));
        verify(reading).apply(alice, book.getId(), null, null, TODAY, null);
        service.update(alice, book.getId(), new UpsertRequest(LibraryStatus.READ, null, null));
        verify(reading).apply(alice, book.getId(), 400, ReadingProgressService.HUNDRED, TODAY, TODAY);
    }

    @Test
    void favoriteChangeDoesNotTouchProgressAndRatingZeroClears() {
        UserLibraryItem item = new UserLibraryItem(alice, book.getId(), LibraryStatus.READING);
        item.update(null, null, 4);
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.of(item));
        service.update(alice, book.getId(), new UpsertRequest(null, true, 0));
        verify(reading, never()).apply(any(), any(), any(), any(), any(), any());
        assertThat(item.getRating()).isNull();
    }

    @Test
    void firstProgressMovesWantToReadToReadingAndFullProgressToRead() {
        UserLibraryItem item = new UserLibraryItem(alice, book.getId(), LibraryStatus.WANT_TO_READ);
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.of(item));
        when(reading.save(any(), any(), any())).thenReturn(progress(BigDecimal.valueOf(10)));
        service.saveProgress(alice, book.getId(), new UpdateRequest(40, null, null, null, null));
        assertThat(item.getStatus()).isEqualTo(LibraryStatus.READING);

        when(reading.save(any(), any(), any())).thenReturn(progress(BigDecimal.valueOf(100)));
        service.saveProgress(alice, book.getId(), new UpdateRequest(400, null, null, null, null));
        assertThat(item.getStatus()).isEqualTo(LibraryStatus.READ);
    }

    @Test
    void progressOnABookNotOnTheShelfAddsItAsReading() {
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.empty());
        when(reading.save(any(), any(), any())).thenReturn(progress(BigDecimal.valueOf(5)));
        service.saveProgress(alice, book.getId(), new UpdateRequest(20, null, null, null, null));
        verify(repo)
                .save(argThat(i ->
                        i.getStatus() == LibraryStatus.READING && i.getUserId().equals(alice)));
    }

    @Test
    void patchAndRemoveOnSomeoneElsesBookAre404AndRemoveDeletesProgress() {
        when(repo.findByUserIdAndBookId(bob, book.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(bob, book.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(bob, book.getId(), new UpsertRequest(null, true, null)))
                .isInstanceOf(NotFoundException.class);
        verify(repo, never()).delete(any());

        UserLibraryItem item = new UserLibraryItem(alice, book.getId(), LibraryStatus.READ);
        when(repo.findByUserIdAndBookId(alice, book.getId())).thenReturn(Optional.of(item));
        service.remove(alice, book.getId());
        verify(reading).delete(alice, book.getId());
    }

    private ProgressView progress(BigDecimal pct) {
        return new ProgressView(book.getId(), true, 0, pct, null, null, null);
    }
}
