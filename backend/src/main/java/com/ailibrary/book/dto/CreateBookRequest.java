package com.ailibrary.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Manual registration. ISBNs are digits only (strip hyphens client-side); the year upper bound is checked in BookService. */
public record CreateBookRequest(
        @Pattern(regexp = "^\\d{13}$", message = "must be 13 digits") String isbn13,
        @Pattern(regexp = "^\\d{9}[\\dX]$", message = "must be 9 digits followed by a digit or X") String isbn10,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 500) String subtitle,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> authors,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> categories,
        @Size(max = 10_000) String description,
        @Size(max = 16) String language,
        @Size(max = 300) String publisher,
        @Min(0) Integer publishedYear,
        @Min(1) @Max(20_000) Integer pageCount,
        @Size(max = 1000) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String coverUrl,
        Boolean publicDomain) {}
