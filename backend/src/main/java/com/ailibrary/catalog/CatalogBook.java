package com.ailibrary.catalog;

import java.util.List;

public record CatalogBook(
        String provider,
        String externalId,
        String title,
        String subtitle,
        List<String> authors,
        String isbn13,
        String isbn10,
        String description,
        List<String> categories,
        String language,
        String publisher,
        Integer publishedYear,
        Integer pageCount,
        String coverUrl,
        boolean publicDomain,
        String sourceUrl
) {}
