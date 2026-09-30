package com.ailibrary.common.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

class VectorFiltersTest {
    @Test
    void documentFilterAlwaysContainsOwnerAndDocument() {
        UUID owner = UUID.randomUUID(), doc = UUID.randomUUID();
        String rendered = VectorFilters.documentChunks(owner, doc).toString();
        assertThat(rendered)
                .contains("ownerId")
                .contains(owner.toString())
                .contains("documentId")
                .contains(doc.toString())
                .contains(VectorFilters.TYPE_DOCUMENT_CHUNK);
        assertThat(VectorFilters.documentChunks(owner, doc).type()).isEqualTo(Filter.ExpressionType.AND);
    }

    @Test
    void privateFiltersRejectMissingOwner() {
        assertThatThrownBy(() -> VectorFilters.bookChunks(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ownerId");
    }
}
