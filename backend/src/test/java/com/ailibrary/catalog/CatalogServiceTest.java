package com.ailibrary.catalog;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.domain.ExternalBookReference;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.repository.ExternalBookReferenceRepository;
import com.ailibrary.book.service.BookVectorIndexer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final BookRepository books = mock(BookRepository.class);
    private final ExternalBookReferenceRepository refs = mock(ExternalBookReferenceRepository.class);
    private final BookVectorIndexer indexer = mock(BookVectorIndexer.class);

    static CatalogBook book(String provider, String id, String title, String author, String isbn13, String isbn10) {
        return new CatalogBook(provider, id, title, null, author == null ? List.of() : List.of(author), isbn13, isbn10,
                null, List.of(), null, null, null, null, null, false, null);
    }

    @Test
    void mergeKeepsFirstProviderAndDropsDuplicatesAcrossIsbnForms() {
        CatalogPage ol = new CatalogPage(List.of(book("open-library", "OL1W", "Clean Architecture", "Robert C. Martin", "9780134494166", null)), 10);
        CatalogPage gb = new CatalogPage(List.of(
                book("google-books", "g1", "Clean Architecture (2nd)", "Uncle Bob", null, "0134494164"),
                book("google-books", "g2", "Refactoring", "Martin Fowler", null, null)), 5);
        CatalogPage merged = CatalogService.merge(List.of(ol, gb), 10);
        assertThat(merged.items()).extracting(CatalogBook::externalId).containsExactly("OL1W", "g2");
        assertThat(merged.total()).isEqualTo(15);
    }

    @Test
    void searchSurvivesAFailingProvider() {
        BookCatalogProvider broken = mock(BookCatalogProvider.class);
        when(broken.enabled()).thenReturn(true);
        when(broken.providerName()).thenReturn("broken");
        when(broken.search(any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("down"));
        BookCatalogProvider ok = mock(BookCatalogProvider.class);
        when(ok.enabled()).thenReturn(true);
        when(ok.search(any(), anyInt(), anyInt())).thenReturn(new CatalogPage(List.of(book("ok", "1", "Dune", "Frank Herbert", null, null)), 1));
        var service = new CatalogService(List.of(broken, ok), books, refs, indexer);
        assertThat(service.search("dune", 1, 10).items()).hasSize(1);
    }

    @Test
    void importReusesExistingBookFoundByIsbn10AndOnlyAddsReference() {
        BookCatalogProvider provider = mock(BookCatalogProvider.class);
        when(provider.enabled()).thenReturn(true);
        when(provider.providerName()).thenReturn("google-books");
        CatalogBook source = book("google-books", "g1", "Clean Architecture", "Robert C. Martin", null, "0134494164");
        when(provider.get("g1")).thenReturn(Optional.of(source));
        Book existing = new Book("9780134494166", null, "Clean Architecture", null, "Robert C. Martin", null, null, null, null, null, null, null, false);
        when(refs.findByProviderAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(refs.save(any())).thenAnswer(i -> i.getArgument(0));
        when(books.findByIsbn13("9780134494166")).thenReturn(Optional.of(existing));

        var service = new CatalogService(List.of(provider), books, refs, indexer);
        var view = service.importBook("google-books", "g1");

        assertThat(view.id()).isEqualTo(existing.getId());
        verify(books, never()).save(any());
        verify(refs).save(any(ExternalBookReference.class));
        verifyNoInteractions(indexer);
    }

    @Test
    void findExistingMatchesNormalizedTitleAndFirstAuthor() {
        Book existing = new Book(null, null, "Café Society", null, "José Saramago | Other", null, null, null, null, null, null, null, false);
        when(books.findTop20ByTitleIgnoreCase("Cafe Society")).thenReturn(List.of());
        when(books.findTop20ByTitleIgnoreCase("Café Society")).thenReturn(List.of(existing));
        var service = new CatalogService(List.of(), books, refs, indexer);
        assertThat(service.findExisting(book("x", "1", "Café Society", "JOSE SARAMAGO", null, null))).contains(existing);
        assertThat(service.findExisting(book("x", "1", "Café Society", "Someone Else", null, null))).isEmpty();
    }
}
