package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookVectorIndexer;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;

/** Regression for the non-UUID vector id bug: a book is indexed with the real local embeddings and found again. */
@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class BookVectorIndexerIT extends PostgresIntegrationTest {
    @Autowired
    BookRepository books;

    @Autowired
    BookVectorIndexer indexer;

    @Autowired
    VectorStoreAccess vectors;

    @Test
    void indexedBookIsFoundBySemanticSearch() {
        Book book = books.save(new Book(
                null,
                null,
                "Surviving on Mars " + System.nanoTime(),
                null,
                "Andy Weir",
                "Science fiction",
                "An astronaut stranded alone on another planet grows potatoes to survive.",
                "en",
                null,
                2011,
                369,
                null,
                false));
        int indexed = indexer.reindexAll();
        assertThat(indexed).isGreaterThanOrEqualTo(1);

        List<Document> hits = vectors.store()
                .orElseThrow()
                .similaritySearch(SearchRequest.builder()
                        .query("stranded astronaut surviving alone on another planet")
                        .topK(5)
                        .similarityThreshold(0.2)
                        .filterExpression(VectorFilters.books())
                        .build());
        assertThat(hits)
                .extracting(d -> d.getMetadata().get("bookId"))
                .contains(book.getId().toString());
    }
}
