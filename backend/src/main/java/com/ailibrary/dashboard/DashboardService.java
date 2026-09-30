package com.ailibrary.dashboard;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.repository.UserLibraryRepository;
import com.ailibrary.reading.domain.ReadingProgress;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private final UserLibraryRepository library;
    private final ReadingProgressRepository progress;
    private final BookRepository books;
    private final Clock clock;

    @Autowired
    public DashboardService(UserLibraryRepository library, ReadingProgressRepository progress, BookRepository books) {
        this(library, progress, books, Clock.systemUTC());
    }

    DashboardService(UserLibraryRepository library, ReadingProgressRepository progress, BookRepository books, Clock clock) {
        this.library = library;
        this.progress = progress;
        this.books = books;
        this.clock = clock;
    }

    public record CurrentlyReading(UUID bookId, String title, String coverUrl, int currentPage, Integer pageCount, double percentage) {}

    public record Dashboard(long totalBooks, long wantToRead, long reading, long read, long dropped, long favorites,
                            long pagesTracked, double averageProgress, Double averageRating, long completedThisYear,
                            List<CurrentlyReading> currentlyReading) {}

    @Transactional(readOnly = true)
    public Dashboard get(UUID userId) {
        List<UserLibraryItem> items = library.findByUserIdOrderByAddedAtDesc(userId);
        Map<UUID, ReadingProgress> progressByBook = progress.findByUserId(userId).stream()
                .collect(Collectors.toMap(ReadingProgress::getBookId, Function.identity(), (a, b) -> a));
        Map<LibraryStatus, Long> byStatus = items.stream()
                .collect(Collectors.groupingBy(UserLibraryItem::getStatus, () -> new EnumMap<>(LibraryStatus.class), Collectors.counting()));

        long pages = progressByBook.values().stream().mapToLong(ReadingProgress::getCurrentPage).sum();
        OptionalDouble rating = items.stream().filter(i -> i.getRating() != null).mapToInt(UserLibraryItem::getRating).average();
        int year = LocalDate.now(clock).getYear();
        long completedThisYear = progressByBook.values().stream()
                .filter(p -> p.getCompletedAt() != null && p.getCompletedAt().getYear() == year).count();

        List<UUID> readingIds = items.stream().filter(i -> i.getStatus() == LibraryStatus.READING).map(UserLibraryItem::getBookId).toList();
        Map<UUID, Book> readingBooks = new HashMap<>();
        books.findAllById(readingIds).forEach(b -> readingBooks.put(b.getId(), b));
        List<CurrentlyReading> current = readingIds.stream().map(readingBooks::get).filter(Objects::nonNull).map(b -> {
            ReadingProgress p = progressByBook.get(b.getId());
            return new CurrentlyReading(b.getId(), b.getTitle(), b.getCoverUrl(), p == null ? 0 : p.getCurrentPage(), b.getPageCount(),
                    p == null || p.getPercentage() == null ? 0 : p.getPercentage().doubleValue());
        }).toList();
        double averageProgress = current.stream().mapToDouble(CurrentlyReading::percentage).average().orElse(0);

        return new Dashboard(items.size(),
                byStatus.getOrDefault(LibraryStatus.WANT_TO_READ, 0L),
                byStatus.getOrDefault(LibraryStatus.READING, 0L),
                byStatus.getOrDefault(LibraryStatus.READ, 0L),
                byStatus.getOrDefault(LibraryStatus.DROPPED, 0L),
                items.stream().filter(UserLibraryItem::isFavorite).count(),
                pages, averageProgress,
                rating.isPresent() ? Math.round(rating.getAsDouble() * 10) / 10.0 : null,
                completedThisYear, current);
    }
}
