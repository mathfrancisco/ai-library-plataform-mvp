package com.ailibrary.catalog;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogFingerprintTest {
    @Test void catalogBookKeepsProviderIdentity() {
        var book = new CatalogBook("open-library", "/works/OL1W", "Clean Architecture", null,
                List.of("Robert C. Martin"), "9780134494166", null, "Software architecture book",
                List.of("software"), "eng", "Pearson", 2017, 432, null, false,
                "https://openlibrary.org/works/OL1W");
        assertThat(book.provider()).isEqualTo("open-library");
        assertThat(book.isbn13()).isEqualTo("9780134494166");
    }
}
