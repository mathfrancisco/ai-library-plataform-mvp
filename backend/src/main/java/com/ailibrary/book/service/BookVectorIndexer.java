package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class BookVectorIndexer {
    private static final Logger log = LoggerFactory.getLogger(BookVectorIndexer.class);
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public BookVectorIndexer(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @Async
    public void index(Book book) {
        VectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) return;
        String text = String.join("\n",
                book.getTitle(),
                nullToEmpty(book.getSubtitle()),
                nullToEmpty(book.getAuthorNames()),
                nullToEmpty(book.getCategoryNames()),
                nullToEmpty(book.getDescription()));
        Document document = Document.builder()
                .id("book-" + book.getId())
                .text(text)
                .metadata(Map.of(
                        "type", "book",
                        "bookId", book.getId().toString(),
                        "title", book.getTitle()
                ))
                .build();
        try {
            store.add(List.of(document));
        } catch (RuntimeException ex) {
            log.warn("Book vector indexing skipped for {}: {}", book.getId(), ex.getMessage());
        }
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
