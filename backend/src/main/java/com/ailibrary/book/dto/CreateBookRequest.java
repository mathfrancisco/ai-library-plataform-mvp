package com.ailibrary.book.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBookRequest(
        @Size(max=20) String isbn13,
        @Size(max=20) String isbn10,
        @NotBlank @Size(max=500) String title,
        @Size(max=500) String subtitle,
        String authorNames,
        String categoryNames,
        String description,
        @Size(max=16) String language,
        @Size(max=300) String publisher,
        @Min(0) @Max(2100) Integer publishedYear,
        @Min(1) @Max(100000) Integer pageCount,
        @Pattern(regexp="^https?://.*", message="must be an http(s) URL") String coverUrl,
        Boolean publicDomain
) {}
