package com.ailibrary.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailibrary.ai.AiFacade;
import com.ailibrary.ai.ModelTier;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.catalog.CatalogService;
import com.ailibrary.common.error.AiException;
import com.ailibrary.common.vector.VectorStoreAccess;
import com.ailibrary.search.SearchDtos.DiscoveryPlan;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryRoutingTest {
    private final AiFacade ai = mock(AiFacade.class);
    private final UUID user = UUID.randomUUID();

    @Test
    void discoveryFallsBackToSmartOnceWhenFastOutputIsInvalid() {
        var search = new HybridSearchService(
                mock(BookRepository.class), mock(CatalogService.class), mock(VectorStoreAccess.class), ai);
        DiscoveryPlan plan = new DiscoveryPlan("space survival", "en", null, List.of());
        when(ai.structured(any(), any(), eq(ModelTier.FAST), any(), any(), eq(DiscoveryPlan.class)))
                .thenThrow(new IllegalStateException("schema validation failed"));
        when(ai.structured(any(), any(), eq(ModelTier.SMART), any(), any(), eq(DiscoveryPlan.class)))
                .thenReturn(plan);
        assertThat(search.plan(user, "books about surviving on another planet")).isEqualTo(plan);
        verify(ai, times(2)).structured(any(), any(), any(), any(), any(), any());
    }

    @Test
    void discoverySurfacesAiUnavailableInsteadOfRetrying() {
        var search = new HybridSearchService(
                mock(BookRepository.class), mock(CatalogService.class), mock(VectorStoreAccess.class), ai);
        when(ai.structured(any(), any(), any(), any(), any(), any())).thenThrow(AiException.disabled());
        assertThatThrownBy(() -> search.plan(user, "x")).isInstanceOf(AiException.class);
        verify(ai, times(1)).structured(any(), any(), any(), any(), any(), any());
    }
}
