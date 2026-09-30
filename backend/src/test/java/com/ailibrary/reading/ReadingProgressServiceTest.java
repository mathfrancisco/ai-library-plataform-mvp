package com.ailibrary.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.reading.domain.ReadingProgress;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import com.ailibrary.reading.service.ReadingProgressService;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReadingProgressServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private final ReadingProgressRepository repo = mock(ReadingProgressRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final UUID user = UUID.randomUUID();
    private final Book book =
            new Book(null, null, "Dune", null, "Frank Herbert", null, null, null, null, null, 400, null, false);
    private ReadingProgressService service;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<ReadingProgressService> c = ReadingProgressService.class.getDeclaredConstructor(
                ReadingProgressRepository.class, BookRepository.class, Clock.class);
        c.setAccessible(true);
        service =
                c.newInstance(repo, books, Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(books.findById(book.getId())).thenReturn(Optional.of(book));
        when(repo.findByUserIdAndBookId(any(), any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void derivesPercentageAndStartDateFromPage() {
        var view = service.upsert(user, book.getId(), new UpdateRequest(100, null, null, null, null));
        assertThat(view.percentage()).isEqualByComparingTo("25.00");
        assertThat(view.startedAt()).isEqualTo(TODAY);
    }

    @Test
    void rejectsPageBeyondBookAndCompletionBeforeStart() {
        assertThatThrownBy(() -> service.upsert(user, book.getId(), new UpdateRequest(401, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upsert(
                        user, book.getId(), new UpdateRequest(null, null, TODAY, TODAY.minusDays(1), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void readingStatusSetsStartDateOnce() {
        service.onStatusChanged(user, book.getId(), LibraryStatus.READING);
        ArgumentCaptor<ReadingProgress> saved = ArgumentCaptor.forClass(ReadingProgress.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStartedAt()).isEqualTo(TODAY);
        assertThat(saved.getValue().getCompletedAt()).isNull();
    }

    @Test
    void readStatusCompletesProgress() {
        service.onStatusChanged(user, book.getId(), LibraryStatus.READ);
        ArgumentCaptor<ReadingProgress> saved = ArgumentCaptor.forClass(ReadingProgress.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getCompletedAt()).isEqualTo(TODAY);
        assertThat(saved.getValue().getPercentage()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(saved.getValue().getCurrentPage()).isEqualTo(400);
    }

    @Test
    void otherStatusesDoNotTouchProgress() {
        service.onStatusChanged(user, book.getId(), LibraryStatus.DROPPED);
        service.onStatusChanged(user, book.getId(), LibraryStatus.WANT_TO_READ);
        verify(repo, never()).save(any());
    }
}
