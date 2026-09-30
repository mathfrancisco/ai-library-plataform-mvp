package com.ailibrary.recommendation;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookFingerprint;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.book.service.SimilarBookService;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import com.ailibrary.library.domain.LibraryStatus;
import com.ailibrary.library.domain.UserLibraryItem;
import com.ailibrary.library.repository.UserLibraryRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Explainable recommendations without an ML pipeline:
 * rules (author/category affinity weighted by favorite, rating and status) fused with
 * vector similarity to the user's strongest seed books. Books already on the shelf are excluded.
 */
@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    static final int RRF_K = 60;
    static final int MAX_SEEDS = 5;
    static final int MAX_TERMS = 6;

    private final UserLibraryRepository library;
    private final BookRepository books;
    private final VectorStoreAccess vectors;

    private final double similarityThreshold;

    public RecommendationService(UserLibraryRepository library, BookRepository books, VectorStoreAccess vectors) {
        this(library, books, vectors, 0.30);
    }

    /** Threshold tuned for all-MiniLM-L6-v2 cosine similarity (SPEC-04 §7.3). */
    @Autowired
    public RecommendationService(
            UserLibraryRepository library,
            BookRepository books,
            VectorStoreAccess vectors,
            @Value("${app.recommendation.similarity-threshold:0.30}") double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
        this.library = library;
        this.books = books;
        this.vectors = vectors;
    }

    public record Recommendation(BookView book, double score, List<String> reasons) {}

    /** Not transactional: the vector search computes an embedding and must not hold a DB connection. */
    public List<Recommendation> forUser(UUID userId, int limit) {
        List<UserLibraryItem> items = library.findByUserIdOrderByAddedAtDesc(userId);
        if (items.isEmpty()) return coldStart(limit);

        Set<UUID> owned = items.stream().map(UserLibraryItem::getBookId).collect(Collectors.toSet());
        Map<UUID, Book> ownedBooks = new HashMap<>();
        books.findAllById(owned).forEach(b -> ownedBooks.put(b.getId(), b));
        Set<String> ownedFingerprints = ownedBooks.values().stream()
                .map(RecommendationService::fingerprint)
                .collect(Collectors.toSet());

        Affinity affinity = Affinity.from(items, ownedBooks);
        if (affinity.isEmpty()) return List.of();

        Map<UUID, Candidate> candidates = new LinkedHashMap<>();
        int rank = 1;
        for (Book b : ruleCandidates(affinity, owned)) {
            if (ownedFingerprints.contains(fingerprint(b))) continue;
            List<String> reasons = affinity.explain(b);
            if (reasons.isEmpty()) continue;
            candidates.computeIfAbsent(b.getId(), k -> new Candidate(b)).add(1.0 / (RRF_K + rank++), reasons);
        }
        rank = 1;
        for (Book b : vectorCandidates(affinity, owned, limit)) {
            if (ownedFingerprints.contains(fingerprint(b))) continue;
            candidates
                    .computeIfAbsent(b.getId(), k -> new Candidate(b))
                    .add(1.0 / (RRF_K + rank++), List.of("Similar to " + affinity.seedTitles()));
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(c -> c.book.getTitle()))
                .limit(limit)
                .map(Candidate::toView)
                .toList();
    }

    private List<Recommendation> coldStart(int limit) {
        return books.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).stream()
                .map(b -> new Recommendation(BookMapper.toView(b), 0, List.of("Recently added to the catalog")))
                .toList();
    }

    private List<Book> ruleCandidates(Affinity affinity, Set<UUID> owned) {
        String query = affinity.topTerms(MAX_TERMS).stream()
                .map(t -> "\"" + t.replace("\"", " ") + "\"")
                .collect(Collectors.joining(" or "));
        if (query.isBlank()) return List.of();
        try {
            List<Book> found = books.lexicalSearch(query, 100).stream()
                    .filter(b -> !owned.contains(b.getId()))
                    .toList();
            // Rank by rule score, not text rank: author affinity dominates category affinity.
            return found.stream()
                    .sorted(Comparator.comparingDouble(affinity::score).reversed())
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Rule-based recommendation query failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<Book> vectorCandidates(Affinity affinity, Set<UUID> owned, int limit) {
        var store = vectors.store();
        if (store.isEmpty()) return List.of();
        String query = affinity.seeds().stream()
                .map(b -> b.getTitle() + " " + Objects.toString(b.getCategoryNames(), "") + " "
                        + Objects.toString(b.getDescription(), ""))
                .collect(Collectors.joining("\n"));
        if (query.isBlank()) return List.of();
        try {
            List<Document> docs = store.get()
                    .similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(Math.min(100, Math.max(20, limit * 3)))
                            .similarityThreshold(similarityThreshold)
                            .filterExpression(VectorFilters.books())
                            .build());
            if (docs == null) return List.of();
            List<UUID> ids = docs.stream()
                    .map(SimilarBookService::bookId)
                    .flatMap(Optional::stream)
                    .filter(id -> !owned.contains(id))
                    .distinct()
                    .toList();
            Map<UUID, Book> byId = new HashMap<>();
            books.findAllById(ids).forEach(b -> byId.put(b.getId(), b));
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        } catch (RuntimeException ex) {
            log.warn("Vector recommendation query failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String fingerprint(Book b) {
        return BookFingerprint.of(
                b.getIsbn13(), b.getIsbn10(), b.getTitle(), BookMapper.toView(b).authors());
    }

    static double seedWeight(UserLibraryItem item) {
        if (item.getStatus() == LibraryStatus.DROPPED) return 0;
        if (item.getRating() != null && item.getRating() <= 2) return 0;
        double weight =
                switch (item.getStatus()) {
                    case READ, READING -> 1.0;
                    case WANT_TO_READ -> 0.5;
                    case DROPPED -> 0;
                };
        if (item.isFavorite()) weight += 3;
        if (item.getRating() != null && item.getRating() >= 4) weight += item.getRating() - 2;
        return weight;
    }

    static final class Affinity {
        final Map<String, Double> authors = new LinkedHashMap<>();
        final Map<String, Double> categories = new LinkedHashMap<>();
        final Map<String, String> labels = new HashMap<>();
        final List<Book> seeds = new ArrayList<>();

        static Affinity from(List<UserLibraryItem> items, Map<UUID, Book> ownedBooks) {
            Affinity a = new Affinity();
            List<Map.Entry<Book, Double>> weighted = new ArrayList<>();
            for (UserLibraryItem item : items) {
                Book b = ownedBooks.get(item.getBookId());
                double w = seedWeight(item);
                if (b == null || w <= 0) continue;
                weighted.add(Map.entry(b, w));
                BookView view = BookMapper.toView(b);
                view.authors().forEach(x -> a.bump(a.authors, x, w));
                view.categories().forEach(x -> a.bump(a.categories, x, w));
            }
            weighted.stream()
                    .sorted(Map.Entry.<Book, Double>comparingByValue().reversed())
                    .limit(MAX_SEEDS)
                    .forEach(e -> a.seeds.add(e.getKey()));
            return a;
        }

        private void bump(Map<String, Double> map, String raw, double w) {
            String key = BookFingerprint.normalizeText(raw);
            if (key.isEmpty()) return;
            map.merge(key, w, Double::sum);
            labels.putIfAbsent(key, raw.trim());
        }

        boolean isEmpty() {
            return seeds.isEmpty();
        }

        List<Book> seeds() {
            return seeds;
        }

        String seedTitles() {
            return seeds.stream().limit(2).map(Book::getTitle).collect(Collectors.joining(" and "));
        }

        List<String> topTerms(int max) {
            List<String> terms = new ArrayList<>();
            top(authors, max / 2).forEach(k -> terms.add(labels.get(k)));
            top(categories, max - terms.size()).forEach(k -> terms.add(labels.get(k)));
            return terms;
        }

        double score(Book b) {
            BookView view = BookMapper.toView(b);
            double s = 0;
            for (String x : view.authors()) s += authors.getOrDefault(BookFingerprint.normalizeText(x), 0.0);
            for (String x : view.categories())
                s += 0.5 * categories.getOrDefault(BookFingerprint.normalizeText(x), 0.0);
            return s;
        }

        List<String> explain(Book b) {
            BookView view = BookMapper.toView(b);
            List<String> reasons = new ArrayList<>();
            view.authors().stream()
                    .filter(x -> authors.containsKey(BookFingerprint.normalizeText(x)))
                    .findFirst()
                    .ifPresent(x -> reasons.add("More from " + x));
            view.categories().stream()
                    .filter(x -> categories.containsKey(BookFingerprint.normalizeText(x)))
                    .findFirst()
                    .ifPresent(x -> reasons.add("Matches your interest in " + x));
            return reasons;
        }

        private static List<String> top(Map<String, Double> map, int n) {
            return map.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(Math.max(0, n))
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    private static final class Candidate {
        final Book book;
        final LinkedHashSet<String> reasons = new LinkedHashSet<>();
        double score;

        Candidate(Book book) {
            this.book = book;
        }

        void add(double value, List<String> why) {
            score += value;
            reasons.addAll(why);
        }

        double score() {
            return score;
        }

        Recommendation toView() {
            return new Recommendation(BookMapper.toView(book), score, List.copyOf(reasons));
        }
    }
}
