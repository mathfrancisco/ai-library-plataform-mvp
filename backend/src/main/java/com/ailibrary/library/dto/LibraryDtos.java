package com.ailibrary.library.dto;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.reading.dto.ReadingDtos.ProgressView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LibraryDtos {
    private LibraryDtos() {}

    /** Null fields are left unchanged; {@code rating: 0} clears the rating. */
    public record UpsertRequest(LibraryStatus status, Boolean favorite, @Min(0) @Max(5) Integer rating) {}

    /** Full detail for one item, including the book description and reading progress. */
    public record LibraryItemView(
            BookView book,
            LibraryStatus status,
            boolean favorite,
            Integer rating,
            Instant addedAt,
            Instant updatedAt,
            ProgressView progress) {}

    /** Compact book fields for lists and assistant tools (no description). */
    public record BookSummary(
            UUID id,
            String title,
            String subtitle,
            List<String> authors,
            List<String> categories,
            String coverUrl,
            Integer publishedYear,
            Integer pageCount) {}

    public record LibraryItemSummary(
            BookSummary book,
            LibraryStatus status,
            boolean favorite,
            Integer rating,
            Instant addedAt,
            Instant updatedAt,
            BigDecimal percentage) {}
}
