package com.ailibrary.book.service;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

@Service
public class SimilarBookService {
    private final BookService bookService;
    private final BookRepository books;
    private final VectorStoreAccess vectors;

    public SimilarBookService(BookService bookService, BookRepository books, VectorStoreAccess vectors) {
        this.bookService = bookService;
        this.books = books;
        this.vectors = vectors;
    }

    public List<BookView> similar(UUID id, int limit) {
        var seed = bookService.getEntity(id);
        var store = vectors.store();
        if (store.isEmpty()) return List.of();
        String query = seed.getTitle() + " " + Objects.toString(seed.getCategoryNames(), "") + " "
                + Objects.toString(seed.getDescription(), "");
        try {
            List<Document> docs = store.get()
                    .similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(Math.min(50, limit + 8))
                            .similarityThreshold(.40)
                            .filterExpression(VectorFilters.books())
                            .build());
            LinkedHashSet<UUID> ids = new LinkedHashSet<>();
            for (Document d : docs) {
                bookId(d).filter(x -> !x.equals(id)).ifPresent(ids::add);
                if (ids.size() >= limit) break;
            }
            return loadInOrder(ids);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<BookView> loadInOrder(Collection<UUID> ids) {
        Map<UUID, BookView> map = new HashMap<>();
        books.findAllById(ids).forEach(b -> map.put(b.getId(), BookMapper.toView(b)));
        return ids.stream().map(map::get).filter(Objects::nonNull).toList();
    }

    public static Optional<UUID> bookId(Document document) {
        Object raw = document.getMetadata().get("bookId");
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw.toString()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
