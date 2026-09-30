package com.ailibrary.search;

import com.ailibrary.book.domain.Book;
import com.ailibrary.catalog.CatalogBook;
import com.ailibrary.search.SearchDtos.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchFusionTest {
    private static Book local(String title, String author, String isbn13) {
        return new Book(isbn13, null, title, null, author, null, null, null, null, null, null, null, false);
    }

    private static CatalogBook external(String id, String title, String author, String isbn13) {
        return new CatalogBook("open-library", id, title, null, List.of(author), isbn13, null, null, List.of(),
                null, null, null, null, null, false, null);
    }

    @Test
    void localAndExternalCopiesOfTheSameBookAreMergedKeepingLocalId() {
        Book clean = local("Clean Architecture", "Robert C. Martin", "9780134494166");
        List<SearchHit> hits = HybridSearchService.fuse(List.of(clean), List.of(),
                List.of(external("OL1W", "Clean Architecture", "Robert C. Martin", "9780134494166"),
                        external("OL2W", "Clean Code", "Robert C. Martin", null)), 10);
        assertThat(hits).hasSize(2);
        SearchHit top = hits.getFirst();
        assertThat(top.localBookId()).isEqualTo(clean.getId());
        assertThat(top.matchType()).isEqualTo("HYBRID");
        assertThat(top.matchedBy()).containsExactly("LEXICAL", "EXTERNAL");
        assertThat(top.score()).isEqualTo(HybridSearchService.rrf(1, HybridSearchService.LEXICAL_WEIGHT)
                + HybridSearchService.rrf(1, HybridSearchService.EXTERNAL_WEIGHT));
    }

    @Test
    void semanticAndLexicalAgreementOutranksSingleSource() {
        Book a = local("Distributed Systems", "Tanenbaum", null);
        Book b = local("Designing Data-Intensive Applications", "Kleppmann", null);
        List<SearchHit> hits = HybridSearchService.fuse(List.of(a, b), List.of(b), List.of(), 10);
        assertThat(hits.getFirst().localBookId()).isEqualTo(b.getId());
    }

    @Test
    void rankingIsDeterministicForTies() {
        List<CatalogBook> ext = List.of(external("1", "Beta", "X", null));
        List<SearchHit> first = HybridSearchService.fuse(List.of(local("Alpha", "Y", null)), List.of(), ext, 10);
        List<SearchHit> second = HybridSearchService.fuse(List.of(local("Alpha", "Y", null)), List.of(), ext, 10);
        assertThat(first).extracting(SearchHit::title).containsExactlyElementsOf(second.stream().map(SearchHit::title).toList());
        assertThat(HybridSearchService.fuse(List.of(), List.of(), ext, 0)).isEmpty();
    }
}
