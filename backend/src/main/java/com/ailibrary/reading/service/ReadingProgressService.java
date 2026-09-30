package com.ailibrary.reading.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.NotFoundException;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.reading.domain.ReadingProgress;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingProgressService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReadingProgressRepository progress;
    private final BookRepository books;
    private final Clock clock;

    @Autowired
    public ReadingProgressService(ReadingProgressRepository progress, BookRepository books) {
        this(progress, books, Clock.systemUTC());
    }

    ReadingProgressService(ReadingProgressRepository progress, BookRepository books, Clock clock) {
        this.progress = progress;
        this.books = books;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProgressView get(UUID userId, UUID bookId) {
        ReadingProgress p = progress.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new NotFoundException("READING_PROGRESS_NOT_FOUND", "Reading progress not found"));
        return view(p);
    }

    @Transactional
    public ProgressView upsert(UUID userId, UUID bookId, UpdateRequest request) {
        Book book = books.findById(bookId).orElseThrow(NotFoundException::book);
        if (request.currentPage() != null
                && book.getPageCount() != null
                && book.getPageCount() > 0
                && request.currentPage() > book.getPageCount())
            throw new BadRequestException("VALIDATION_ERROR", "currentPage exceeds the book's page count");
        ReadingProgress p =
                progress.findByUserIdAndBookId(userId, bookId).orElseGet(() -> new ReadingProgress(userId, bookId));
        BigDecimal percentage = request.percentage();
        if (percentage == null && request.currentPage() != null)
            percentage = derivePercentage(request.currentPage(), book.getPageCount());
        LocalDate startedAt = request.startedAt();
        if (startedAt == null && p.getStartedAt() == null && request.currentPage() != null && request.currentPage() > 0)
            startedAt = today();
        p.update(request.currentPage(), percentage, startedAt, request.completedAt(), request.notes());
        if (p.getStartedAt() != null
                && p.getCompletedAt() != null
                && p.getCompletedAt().isBefore(p.getStartedAt()))
            throw new BadRequestException("VALIDATION_ERROR", "completedAt cannot be before startedAt");
        return view(progress.save(p));
    }

    /** Keeps reading dates consistent with shelf status changes made in the library module. */
    @Transactional
    public void onStatusChanged(UUID userId, UUID bookId, LibraryStatus status) {
        if (status != LibraryStatus.READING && status != LibraryStatus.READ) return;
        Book book = books.findById(bookId).orElseThrow(NotFoundException::book);
        ReadingProgress p =
                progress.findByUserIdAndBookId(userId, bookId).orElseGet(() -> new ReadingProgress(userId, bookId));
        LocalDate today = today();
        if (status == LibraryStatus.READING) {
            p.update(null, null, p.getStartedAt() == null ? today : null, null, null);
        } else {
            Integer lastPage = book.getPageCount() != null && book.getPageCount() > 0 ? book.getPageCount() : null;
            LocalDate started = p.getStartedAt() == null ? today : null;
            p.update(lastPage, HUNDRED, started, p.getCompletedAt() == null ? today : null, null);
        }
        progress.save(p);
    }

    static BigDecimal derivePercentage(int currentPage, Integer pageCount) {
        if (pageCount == null || pageCount <= 0) return null;
        return BigDecimal.valueOf(currentPage)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(pageCount), 2, RoundingMode.HALF_UP)
                .min(HUNDRED);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private ProgressView view(ReadingProgress p) {
        return new ProgressView(
                p.getBookId(),
                p.getCurrentPage(),
                p.getPercentage(),
                p.getStartedAt(),
                p.getCompletedAt(),
                p.getNotes());
    }
}
