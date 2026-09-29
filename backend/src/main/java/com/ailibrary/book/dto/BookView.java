package com.ailibrary.book.dto;

import java.util.List;
import java.util.UUID;

public record BookView(
        UUID id,
        String isbn13,
        String isbn10,
        String title,
        String subtitle,
        List<String> authors,
        List<String> categories,
        String description,
        String language,
        String publisher,
        Integer publishedYear,
        Integer pageCount,
        String coverUrl,
        boolean publicDomain
) {}
