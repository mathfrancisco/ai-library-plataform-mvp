package com.ailibrary.reading.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.reading.domain.ReadingProgress;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import com.ailibrary.reading.dto.ReadingDtos.UpdateRequest;
import com.ailibrary.reading.repository.ReadingProgressRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Progress storage and progress-only validation. Rules that link progress and shelf status live in
 * {@code LibraryService} (SPEC-04 §5.3), which calls the primitives below.
 */
@Service
public class ReadingProgressService {
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReadingProgressRepository progress;

    public ReadingProgressService(ReadingProgressRepository progress) {
        this.progress = progress;
    }

    @Transactional(readOnly = true)
    public ProgressView get(UUID userId, UUID bookId) {
        return progress.findByUserIdAndBookId(userId, bookId)
                .map(ReadingProgressService::view)
                .orElseGet(() -> ProgressView.empty(bookId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProgressView> forBooks(UUID userId, Collection<UUID> bookIds) {
        if (bookIds.isEmpty()) return Map.of();
        return progress.findByUserIdAndBookIdIn(userId, bookIds).stream()
                .map(ReadingProgressService::view)
                .collect(Collectors.toMap(ProgressView::bookId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<ReadingProgress> all(UUID userId) {
        return progress.findByUserId(userId);
    }

    /** Saves the requested fields; derives percentage from the page when the page count is known. */
    @Transactional
    public ProgressView save(UUID userId, Book book, UpdateRequest request) {
        Integer pageCount = book.getPageCount();
        if (request.currentPage() != null && pageCount != null && pageCount > 0 && request.currentPage() > pageCount)
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "currentPage exceeds the book's page count");
        ReadingProgress p = load(userId, book.getId());
        BigDecimal percentage = request.percentage();
        if (percentage == null && request.currentPage() != null)
            percentage = derivePercentage(request.currentPage(), pageCount);
        p.update(request.currentPage(), percentage, request.startedAt(), request.completedAt(), request.notes());
        if (p.getStartedAt() != null
                && p.getCompletedAt() != null
                && p.getCompletedAt().isBefore(p.getStartedAt()))
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "completedAt cannot be before startedAt");
        return view(progress.save(p));
    }

    /** Applies date/percentage changes decided by a caller-side rule; nulls leave fields unchanged. */
    @Transactional
    public ProgressView apply(
            UUID userId,
            UUID bookId,
            Integer currentPage,
            BigDecimal percentage,
            LocalDate startedAt,
            LocalDate completedAt) {
        ReadingProgress p = load(userId, bookId);
        p.update(
                currentPage,
                percentage,
                p.getStartedAt() == null ? startedAt : null,
                p.getCompletedAt() == null ? completedAt : null,
                null);
        return view(progress.save(p));
    }

    @Transactional
    public void delete(UUID userId, UUID bookId) {
        progress.deleteByUserIdAndBookId(userId, bookId);
    }

    public static BigDecimal derivePercentage(int currentPage, Integer pageCount) {
        if (pageCount == null || pageCount <= 0) return null;
        return BigDecimal.valueOf(currentPage)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(pageCount), 2, RoundingMode.HALF_UP)
                .min(HUNDRED);
    }

    private ReadingProgress load(UUID userId, UUID bookId) {
        return progress.findByUserIdAndBookId(userId, bookId).orElseGet(() -> new ReadingProgress(userId, bookId));
    }

    static ProgressView view(ReadingProgress p) {
        return new ProgressView(
                p.getBookId(),
                true,
                p.getCurrentPage(),
                p.getPercentage(),
                p.getStartedAt(),
                p.getCompletedAt(),
                p.getNotes());
    }
}
