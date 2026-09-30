package com.ailibrary.book.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Deduplication identity shared by catalog federation, import and hybrid search.
 * Priority: normalized ISBN-13, then ISBN-10, then normalized (title + first author).
 * Provider ids are never treated as globally unique.
 */
public final class BookFingerprint {
    private BookFingerprint() {}

    public static String of(String isbn13, String isbn10, String title, List<String> authors) {
        String i13 = isbn13(isbn13);
        if (i13 != null) return "isbn13:" + i13;
        String i10 = isbn10(isbn10);
        if (i10 != null) {
            String converted = isbn10To13(i10);
            return converted != null ? "isbn13:" + converted : "isbn10:" + i10;
        }
        return "title:" + normalizeText(title) + "::" + normalizeText(firstAuthor(authors));
    }

    public static String isbn13(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[^0-9]", "");
        return cleaned.length() == 13 ? cleaned : null;
    }

    public static String isbn10(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        return cleaned.matches("[0-9]{9}[0-9X]") ? cleaned : null;
    }

    /** ISBN-10 and its 978-prefixed ISBN-13 identify the same edition. */
    public static String isbn10To13(String isbn10) {
        String i10 = isbn10(isbn10);
        if (i10 == null) return null;
        String base = "978" + i10.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (base.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return base + ((10 - sum % 10) % 10);
    }

    public static String firstAuthor(List<String> authors) {
        return authors == null || authors.isEmpty() ? "" : authors.getFirst();
    }

    public static String normalizeText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
