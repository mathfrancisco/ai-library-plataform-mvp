package com.ailibrary.recommendation;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.library.repository.UserLibraryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RecommendationService {
    private final UserLibraryRepository library;
    private final BookRepository books;
    private final VectorStore vectors;

    public RecommendationService(UserLibraryRepository library, BookRepository books, VectorStore vectors) {
        this.library = library; this.books = books; this.vectors = vectors;
    }

    @Transactional(readOnly = true)
    public List<BookView> forUser(UUID userId, int limit) {
        var items = library.findByUserIdOrderByAddedAtDesc(userId);
        if (items.isEmpty()) return books.findAll().stream().limit(limit).map(BookMapper::toView).toList();
        var owned = new HashSet<>(library.findBookIds(userId));
        var seedBooks = books.findAllById(items.stream().filter(i -> i.isFavorite() || (i.getRating()!=null && i.getRating()>=4))
                .map(i -> i.getBookId()).limit(5).toList());
        if (seedBooks.isEmpty()) seedBooks = books.findAllById(items.stream().map(i -> i.getBookId()).limit(3).toList());
        String query = seedBooks.stream().map(b -> b.getTitle()+" "+Objects.toString(b.getDescription(),"")+" "+Objects.toString(b.getCategoryNames(),"")).reduce("", (a,b)->a+" "+b);
        if (query.isBlank()) return List.of();
        List<Document> docs;
        try { docs = vectors.similaritySearch(SearchRequest.builder().query(query).topK(Math.max(20, limit * 3)).similarityThreshold(.45).filterExpression("type == 'book'").build()); }
        catch (RuntimeException ex) { return List.of(); }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (Document d: docs) {
            Object raw = d.getMetadata().get("bookId");
            if (raw == null) continue;
            try { UUID id=UUID.fromString(raw.toString()); if(!owned.contains(id)) ids.add(id); } catch(Exception ignored) {}
            if(ids.size()>=limit) break;
        }
        Map<UUID, BookView> mapped = new HashMap<>();
        books.findAllById(ids).forEach(b -> mapped.put(b.getId(), BookMapper.toView(b)));
        return ids.stream().map(mapped::get).filter(Objects::nonNull).toList();
    }
}
