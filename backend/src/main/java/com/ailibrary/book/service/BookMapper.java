package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.dto.BookView;
import java.util.Arrays;
import java.util.List;

public final class BookMapper {
    private BookMapper() {}

    public static BookView toView(Book book) {
        return new BookView(
                book.getId(),
                book.getIsbn13(),
                book.getIsbn10(),
                book.getTitle(),
                book.getSubtitle(),
                split(book.getAuthorNames()),
                split(book.getCategoryNames()),
                book.getDescription(),
                book.getLanguage(),
                book.getPublisher(),
                book.getPublishedYear(),
                book.getPageCount(),
                book.getCoverUrl(),
                book.isPublicDomain());
    }

    public static String join(List<String> values) {
        if (values == null) return null;
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + " | " + b)
                .orElse(null);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\s*\\|\\s*"))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
