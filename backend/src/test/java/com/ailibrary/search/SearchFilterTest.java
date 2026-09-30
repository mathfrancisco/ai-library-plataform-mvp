package com.ailibrary.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchFilterTest {
    @Test
    void languageMatchesAcrossIsoForms() {
        var pt = new HybridSearchService.Filters("pt", List.of());
        assertThat(HybridSearchService.matches(pt, "por", null)).isTrue();
        assertThat(HybridSearchService.matches(pt, "eng", null)).isFalse();
        assertThat(HybridSearchService.matches(pt, null, null)).isTrue();
    }

    @Test
    void categoriesNeedOneOverlapButMissingDataNeverFilters() {
        var scifi = new HybridSearchService.Filters(null, List.of("Science fiction"));
        assertThat(HybridSearchService.matches(scifi, null, "Fiction | Science Fiction"))
                .isTrue();
        assertThat(HybridSearchService.matches(scifi, null, "Cooking")).isFalse();
        assertThat(HybridSearchService.matches(scifi, null, null)).isTrue();
    }
}
