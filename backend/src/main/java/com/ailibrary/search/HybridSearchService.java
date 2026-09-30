package com.ailibrary.search;

import com.ailibrary.ai.AiFacade;
import com.ailibrary.ai.AiPromptTemplates;
import com.ailibrary.ai.ModelTier;
import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookFingerprint;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.book.service.SimilarBookService;
import com.ailibrary.catalog.CatalogBook;
import com.ailibrary.catalog.CatalogService;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import com.ailibrary.search.SearchDtos.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

/**
 * Weighted reciprocal-rank fusion over three ranked lists: local FTS, local vector similarity
 * and the federated external catalog. Hits are merged by the shared book fingerprint so a book
 * found both locally and externally appears once, keeping its local id.
 */
@Service
public class HybridSearchService {
    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
    static final int RRF_K = 60;
    static final double LEXICAL_WEIGHT = 1.0;
    static final double SEMANTIC_WEIGHT = 1.2;
    static final double EXTERNAL_WEIGHT = 0.8;
    static final double SEMANTIC_THRESHOLD = 0.45;

    private final BookRepository books;
    private final CatalogService catalog;
    private final VectorStoreAccess vectors;
    private final AiFacade ai;

    public HybridSearchService(BookRepository books, CatalogService catalog, VectorStoreAccess vectors, AiFacade ai) {
        this.books = books;
        this.catalog = catalog;
        this.vectors = vectors;
        this.ai = ai;
    }

    public List<SearchHit> search(String query, SearchMode mode, int limit) {
        String q = query.trim();
        List<Book> lexical = mode == SearchMode.SEMANTIC ? List.of() : lexical(q, limit);
        List<Book> semantic = mode == SearchMode.LEXICAL ? List.of() : semantic(q, limit);
        List<CatalogBook> external = mode == SearchMode.SEMANTIC ? List.of() : external(q, limit);
        return fuse(lexical, semantic, external, limit);
    }

    static List<SearchHit> fuse(List<Book> lexical, List<Book> semantic, List<CatalogBook> external, int limit) {
        Map<String, MutableHit> merged = new LinkedHashMap<>();
        addLocal(lexical, "LEXICAL", LEXICAL_WEIGHT, merged);
        addLocal(semantic, "SEMANTIC", SEMANTIC_WEIGHT, merged);
        int rank = 1;
        for (CatalogBook b : external) {
            String key = BookFingerprint.of(b.isbn13(), b.isbn10(), b.title(), b.authors());
            merged.computeIfAbsent(key, k -> MutableHit.external(b)).add(rrf(rank++, EXTERNAL_WEIGHT), "EXTERNAL");
        }
        // Stable ordering: score desc, then title for deterministic ties.
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(MutableHit::score)
                        .reversed()
                        .thenComparing(h -> h.title == null ? "" : h.title))
                .limit(limit)
                .map(MutableHit::toView)
                .toList();
    }

    public DiscoveryResponse discover(UUID userId, String prompt, int limit) {
        DiscoveryPlan plan = plan(userId, prompt);
        String query = plan.query() == null || plan.query().isBlank() ? prompt : plan.query();
        return new DiscoveryResponse(plan, search(query, SearchMode.HYBRID, limit));
    }

    /** FAST model first; one SMART retry when the fast model's output fails schema validation. */
    DiscoveryPlan plan(UUID userId, String prompt) {
        for (ModelTier tier : List.of(ModelTier.FAST, ModelTier.SMART)) {
            try {
                return ai.structured(
                        userId,
                        "DISCOVERY_QUERY",
                        tier,
                        AiPromptTemplates.DISCOVERY_SYSTEM,
                        prompt,
                        DiscoveryPlan.class);
            } catch (ApiException unavailable) {
                throw unavailable; // disabled, rate limited, timeout: surface to the client
            } catch (RuntimeException invalidOutput) {
                log.debug("Discovery plan with {} model failed: {}", tier, invalidOutput.getMessage());
            }
        }
        return new DiscoveryPlan(prompt, null, null, List.of());
    }

    private List<Book> lexical(String query, int limit) {
        try {
            return books.lexicalSearch(query, limit);
        } catch (RuntimeException ex) {
            log.warn("Lexical search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<Book> semantic(String query, int limit) {
        var store = vectors.store();
        if (store.isEmpty()) return List.of();
        try {
            List<Document> docs = store.get()
                    .similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(limit)
                            .similarityThreshold(SEMANTIC_THRESHOLD)
                            .filterExpression(VectorFilters.books())
                            .build());
            if (docs == null) return List.of();
            List<UUID> ids = docs.stream()
                    .map(SimilarBookService::bookId)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList();
            Map<UUID, Book> byId = new HashMap<>();
            books.findAllById(ids).forEach(b -> byId.put(b.getId(), b));
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        } catch (RuntimeException ex) {
            log.warn("Semantic search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<CatalogBook> external(String query, int limit) {
        return catalog.search(query, 1, limit).items();
    }

    private static void addLocal(List<Book> ranked, String matchType, double weight, Map<String, MutableHit> out) {
        int rank = 1;
        for (Book b : ranked) {
            var view = BookMapper.toView(b);
            String key = BookFingerprint.of(b.getIsbn13(), b.getIsbn10(), b.getTitle(), view.authors());
            out.computeIfAbsent(key, k -> MutableHit.local(b)).add(rrf(rank++, weight), matchType);
        }
    }

    static double rrf(int rank, double weight) {
        return weight / (RRF_K + rank);
    }

    private static final class MutableHit {
        UUID localBookId;
        String provider, externalId, title, cover, description;
        List<String> authors;
        final LinkedHashSet<String> matchedBy = new LinkedHashSet<>();
        double score;

        static MutableHit local(Book b) {
            var h = new MutableHit();
            h.localBookId = b.getId();
            h.provider = "local";
            h.title = b.getTitle();
            h.authors = BookMapper.toView(b).authors();
            h.cover = b.getCoverUrl();
            h.description = b.getDescription();
            return h;
        }

        static MutableHit external(CatalogBook b) {
            var h = new MutableHit();
            h.provider = b.provider();
            h.externalId = b.externalId();
            h.title = b.title();
            h.authors = b.authors();
            h.cover = b.coverUrl();
            h.description = b.description();
            return h;
        }

        void add(double value, String matchType) {
            score += value;
            matchedBy.add(matchType);
        }

        double score() {
            return score;
        }

        SearchHit toView() {
            String matchType =
                    matchedBy.size() > 1 ? "HYBRID" : matchedBy.iterator().next();
            return new SearchHit(
                    localBookId,
                    provider,
                    externalId,
                    title,
                    authors == null ? List.of() : authors,
                    cover,
                    description,
                    score,
                    matchType,
                    List.copyOf(matchedBy));
        }
    }
}
