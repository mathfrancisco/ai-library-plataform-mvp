package com.ailibrary.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CatalogProviderMappingTest {
    @Test
    void mapsOpenLibrarySearchFixture() throws IOException {
        CatalogPage page = OpenLibraryProvider.mapSearch(CatalogJson.parse(fixture("open-library-search.json")));
        assertThat(page.total()).isEqualTo(2);
        CatalogBook book = page.items().getFirst();
        assertThat(book.provider()).isEqualTo("open-library");
        assertThat(book.externalId()).isEqualTo("/works/OL17618370W");
        assertThat(book.isbn13()).isEqualTo("9780134494166");
        assertThat(book.isbn10()).isEqualTo("0134494164");
        assertThat(book.coverUrl()).isEqualTo("https://covers.openlibrary.org/b/id/8290435-L.jpg");
        assertThat(book.pageCount()).isEqualTo(404);
        assertThat(book.categories()).containsExactly("Software architecture", "Computer software");
        assertThat(page.items().get(1).authors()).isEmpty();
        assertThat(page.items().get(1).publicDomain()).isTrue();
    }

    @Test
    void enrichesOpenLibraryBookWithWorkDescription() throws IOException {
        CatalogBook book = OpenLibraryProvider.mapSearch(CatalogJson.parse(fixture("open-library-search.json")))
                .items()
                .getFirst();
        CatalogBook detailed =
                OpenLibraryProvider.withWorkDetails(book, CatalogJson.parse(fixture("open-library-work.json")));
        assertThat(detailed.description()).isEqualTo("Practical software architecture solutions from Uncle Bob.");
        assertThat(detailed.categories()).containsExactly("Software architecture", "Computer software");
    }

    @Test
    void acceptsOnlyWorkKeys() {
        assertThat(OpenLibraryProvider.workKey("OL123W")).isEqualTo("/works/OL123W");
        assertThat(OpenLibraryProvider.workKey("/works/OL123W")).isEqualTo("/works/OL123W");
        assertThat(OpenLibraryProvider.workKey("/works/OL123W/../../admin")).isNull();
    }

    @Test
    void mapsGoogleBooksFixture() throws IOException {
        CatalogPage page = GoogleBooksProvider.mapSearch(CatalogJson.parse(fixture("google-books-search.json")));
        CatalogBook book = page.items().getFirst();
        assertThat(book.provider()).isEqualTo("google-books");
        assertThat(book.isbn13()).isEqualTo("9780321125217");
        assertThat(book.publishedYear()).isEqualTo(2003);
        assertThat(book.pageCount()).isEqualTo(560);
        assertThat(book.coverUrl()).startsWith("https://");
        assertThat(book.description()).contains("complexity");
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = CatalogProviderMappingTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
