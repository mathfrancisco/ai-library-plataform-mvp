package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BookVectorIndexer {
    private static final Logger log = LoggerFactory.getLogger(BookVectorIndexer.class);
    private final VectorStoreAccess vectors;

    public BookVectorIndexer(VectorStoreAccess vectors) {
        this.vectors = vectors;
    }

    /** Re-embeds every local book in batches; used after enabling AI or changing the embedding model. */
    public int reindexAll(BookRepository books) {
        var store = vectors.store()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "VECTOR_DISABLED",
                        "Vector search is disabled (VECTOR_ENABLED=false)"));
        int count = 0;
        var page = books.findAll(PageRequest.of(0, 100, Sort.by("id")));
        while (true) {
            List<Document> batch = page.getContent().stream()
                    .map(BookVectorIndexer::toDocument)
                    .toList();
            if (!batch.isEmpty()) store.add(batch);
            count += batch.size();
            if (!page.hasNext()) return count;
            page = books.findAll(page.nextPageable());
        }
    }

    @Async
    public void index(Book book) {
        vectors.store().ifPresent(store -> {
            try {
                store.add(List.of(toDocument(book)));
            } catch (RuntimeException ex) {
                log.warn("Book vector indexing skipped for {}: {}", book.getId(), ex.getMessage());
            }
        });
    }

    /** Stable id per book so re-indexing replaces instead of duplicating the vector row. */
    static String vectorId(UUID bookId) {
        return UUID.nameUUIDFromBytes(("book-" + bookId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    static Document toDocument(Book book) {
        String text = String.join(
                        "\n",
                        book.getTitle(),
                        Objects.toString(book.getSubtitle(), ""),
                        Objects.toString(book.getAuthorNames(), ""),
                        Objects.toString(book.getCategoryNames(), ""),
                        Objects.toString(book.getDescription(), ""))
                .strip();
        return Document.builder()
                .id(vectorId(book.getId()))
                .text(text)
                .metadata(Map.of(
                        "type", VectorFilters.TYPE_BOOK,
                        "bookId", book.getId().toString(),
                        "title", book.getTitle()))
                .build();
    }
}
