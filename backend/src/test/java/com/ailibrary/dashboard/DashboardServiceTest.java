package com.ailibrary.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.repository.UserLibraryRepository;
import com.ailibrary.reading.domain.ReadingProgress;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    @Test
    void aggregatesCountsProgressAndRatings() {
        UserLibraryRepository library = mock(UserLibraryRepository.class);
        ReadingProgressRepository progress = mock(ReadingProgressRepository.class);
        BookRepository books = mock(BookRepository.class);
        UUID user = UUID.randomUUID();
        Book reading = new Book(null, null, "Dune", null, null, null, null, null, null, null, 400, null, false);
        UserLibraryItem r = new UserLibraryItem(user, reading.getId(), LibraryStatus.READING);
        r.update(null, true, 4);
        UserLibraryItem done = new UserLibraryItem(user, UUID.randomUUID(), LibraryStatus.READ);
        done.update(null, null, 5);
        UserLibraryItem want = new UserLibraryItem(user, UUID.randomUUID(), LibraryStatus.WANT_TO_READ);
        ReadingProgress p1 = new ReadingProgress(user, reading.getId());
        p1.update(100, BigDecimal.valueOf(25), null, null, null);
        ReadingProgress p2 = new ReadingProgress(user, done.getBookId());
        p2.update(300, BigDecimal.valueOf(100), null, LocalDate.of(2026, 3, 1), null);
        ReadingProgress lastYear = new ReadingProgress(user, UUID.randomUUID());
        lastYear.update(0, null, null, LocalDate.of(2025, 12, 31), null);
        when(library.findByUserIdOrderByAddedAtDesc(user)).thenReturn(List.of(r, done, want));
        when(progress.findByUserId(user)).thenReturn(List.of(p1, p2, lastYear));
        when(books.findAllById(anyCollection())).thenReturn(List.of(reading));

        var d = new DashboardService(
                        library,
                        progress,
                        books,
                        Clock.fixed(LocalDate.of(2026, 9, 30).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .get(user);

        assertThat(d.totalBooks()).isEqualTo(3);
        assertThat(d.reading()).isEqualTo(1);
        assertThat(d.read()).isEqualTo(1);
        assertThat(d.wantToRead()).isEqualTo(1);
        assertThat(d.dropped()).isZero();
        assertThat(d.favorites()).isEqualTo(1);
        assertThat(d.pagesTracked()).isEqualTo(400);
        assertThat(d.averageProgress()).isEqualTo(25.0);
        assertThat(d.averageRating()).isEqualTo(4.5);
        assertThat(d.completedThisYear()).isEqualTo(1);
        assertThat(d.currentlyReading()).singleElement().satisfies(c -> assertThat(c.title())
                .isEqualTo("Dune"));
    }
}
