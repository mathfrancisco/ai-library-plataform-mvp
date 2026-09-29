package com.ailibrary.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookRequest(
        @Size(max=13) String isbn13,
        @Size(max=10) String isbn10,
        @NotBlank @Size(max=500) String title,
        @Size(max=500) String subtitle,
        String authorNames,
        String categoryNames,
        String description,
        String language,
        String publisher,
        Integer publishedYear,
        Integer pageCount,
        String coverUrl,
        boolean publicDomain
) {}
