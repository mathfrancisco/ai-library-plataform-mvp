package com.ailibrary.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchFilterTest {
    @Test
    void languageMatchesAcrossIsoForms() {
        var pt = new HybridSearchService.Filters("pt", List.of(), null);
        assertThat(HybridSearchService.matches(pt, "por", null, null)).isTrue();
        assertThat(HybridSearchService.matches(pt, "eng", null, null)).isFalse();
        assertThat(HybridSearchService.matches(pt, null, null, null)).isTrue();
    }

    @Test
    void categoriesNeedOneOverlapButMissingDataNeverFilters() {
        var scifi = new HybridSearchService.Filters(null, List.of("Science fiction"), null);
        assertThat(HybridSearchService.matches(scifi, null, "Fiction | Science Fiction", null))
                .isTrue();
        assertThat(HybridSearchService.matches(scifi, null, "Cooking", null)).isFalse();
        assertThat(HybridSearchService.matches(scifi, null, null, null)).isTrue();
    }

    @Test
    void maxPagesDropsLongerBooksButKeepsUnknownLength() {
        var short300 = new HybridSearchService.Filters(null, List.of(), 300);
        assertThat(HybridSearchService.matches(short300, null, null, 250)).isTrue();
        assertThat(HybridSearchService.matches(short300, null, null, 301)).isFalse();
        assertThat(HybridSearchService.matches(short300, null, null, null)).isTrue();
    }
}
