package com.ailibrary.library.dto;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.library.domain.LibraryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

public final class LibraryDtos {
    private LibraryDtos() {}

    public record UpsertRequest(LibraryStatus status, Boolean favorite, @Min(1) @Max(5) Integer rating) {}
    public record LibraryItemView(BookView book, LibraryStatus status, boolean favorite, Integer rating, Instant addedAt) {}
}
