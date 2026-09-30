package com.ailibrary.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailibrary.book.service.BookFingerprint;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookFingerprintTest {
    @Test
    void isbn13WinsAndIgnoresFormatting() {
        assertThat(BookFingerprint.of(
                        "978-0-13-449416-6", "0134494164", "Clean Architecture", List.of("Robert C. Martin")))
                .isEqualTo("isbn13:9780134494166");
    }

    @Test
    void isbn10IsConvertedSoItMatchesTheIsbn13Edition() {
        assertThat(BookFingerprint.of(null, "0-13-449416-4", "Clean Architecture", List.of()))
                .isEqualTo(BookFingerprint.of("9780134494166", null, "Other title", List.of()));
        assertThat(BookFingerprint.isbn10To13("080442957X")).isEqualTo("9780804429573");
    }

    @Test
    void titleAndFirstAuthorAreNormalized() {
        assertThat(BookFingerprint.of(null, null, "Café: Été!", List.of("José Saramago", "Other")))
                .isEqualTo(BookFingerprint.of(null, null, "cafe  ete", List.of("JOSE SARAMAGO")));
    }

    @Test
    void invalidIsbnsFallBackToTitle() {
        assertThat(BookFingerprint.of("123", "abc", "Dune", List.of("Frank Herbert")))
                .isEqualTo("title:dune::frank herbert");
    }
}
