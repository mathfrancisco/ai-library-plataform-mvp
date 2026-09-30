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
import com.ailibrary.catalog.CatalogPage;
import com.ailibrary.catalog.CatalogService;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.vector.VectorFilters;
import com.ailibrary.common.vector.VectorStoreAccess;
import com.ailibrary.search.SearchDtos.*;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Weighted reciprocal-rank fusion over three ranked lists: local FTS, local vector similarity
 * and the federated external catalog. Hits are merged by the shared book fingerprint so a book
 * found both locally and externally appears once, keeping its local id.
 */
@Service
@EnableConfigurationProperties(SearchProperties.class)
public class HybridSearchService {
    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
    static final int RRF_K = 60;
    static final double LEXICAL_WEIGHT = 1.0;
    static final double SEMANTIC_WEIGHT = 1.2;
    static final double EXTERNAL_WEIGHT = 0.8;

    private final BookRepository books;
    private final CatalogService catalog;
    private final VectorStoreAccess vectors;
    private final AiFacade ai;
    private final SearchProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public HybridSearchService(
            BookRepository books,
            CatalogService catalog,
            VectorStoreAccess vectors,
            AiFacade ai,
            SearchProperties properties) {
        this.books = books;
        this.catalog = catalog;
        this.vectors = vectors;
        this.ai = ai;
        this.properties = properties;
    }

    HybridSearchService(BookRepository books, CatalogService catalog, VectorStoreAccess vectors, AiFacade ai) {
        this(books, catalog, vectors, ai, new SearchProperties(null, null, null, null));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Filters from a discovery plan; null fields do not filter. */
    public record Filters(String language, List<String> categories, Integer maxPages) {
        static final Filters NONE = new Filters(null, List.of(), null);
    }

    public SearchResponse search(String query, SearchMode mode, int limit) {
        return search(query, mode, limit, Filters.NONE);
    }

    /** Runs the three branches in parallel, each with its own timeout; a failed branch is reported as degraded. */
    public SearchResponse search(String query, SearchMode mode, int limit, Filters filters) {
        String q = query.trim();
        var lexical = branch(mode != SearchMode.SEMANTIC, () -> lexical(q, limit));
        var semantic = branch(mode != SearchMode.LEXICAL, () -> semantic(q, limit));
        var external = branch(mode != SearchMode.SEMANTIC, () -> catalog.search(q, 1, limit));

        List<String> degraded = new ArrayList<>();
        List<Book> lexicalHits = await(lexical, properties.lexicalTimeout(), "lexical", degraded, List.of());
        List<Book> semanticHits = await(semantic, properties.semanticTimeout(), "semantic", degraded, List.of());
        CatalogPage page =
                await(external, properties.externalTimeout(), "external", degraded, new CatalogPage(List.of(), 0));
        if (!page.complete() && !degraded.contains("external")) degraded.add("external");

        List<SearchHit> hits = fuse(
                lexicalHits.stream()
                        .filter(b -> matches(filters, b.getLanguage(), b.getCategoryNames(), b.getPageCount()))
                        .toList(),
                semanticHits.stream()
                        .filter(b -> matches(filters, b.getLanguage(), b.getCategoryNames(), b.getPageCount()))
                        .toList(),
                page.items().stream()
                        .filter(b -> matches(filters, b.language(), String.join("|", b.categories()), b.pageCount()))
                        .toList(),
                limit);
        return new SearchResponse(hits, List.copyOf(degraded), page.providers());
    }

    private <T> Future<T> branch(boolean enabled, Callable<T> task) {
        return enabled ? executor.submit(task) : null;
    }

    private static <T> T await(Future<T> future, Duration timeout, String name, List<String> degraded, T empty) {
        if (future == null) return empty;
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("Search branch {} timed out after {}", name, timeout);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
            log.warn("Search branch {} failed: {}", name, ex.getCause().toString());
        }
        degraded.add(name);
        return empty;
    }

    /**
     * Language compares ISO 639 codes in 2- or 3-letter form; categories need one overlapping term; page count must
     * not exceed {@code maxPages}. Hits with no language, categories or page count are kept: missing data never
     * filters a book out.
     */
    static boolean matches(Filters filters, String language, String categories, Integer pageCount) {
        if (filters.maxPages() != null && filters.maxPages() > 0 && pageCount != null) {
            if (pageCount > filters.maxPages()) return false;
        }
        if (filters.language() != null
                && language != null
                && !filters.language().isBlank()) {
            if (!iso3(filters.language()).equals(iso3(language))) return false;
        }
        if (filters.categories() != null
                && !filters.categories().isEmpty()
                && categories != null
                && !categories.isBlank()) {
            String have = BookFingerprint.normalizeText(categories);
            return filters.categories().stream()
                    .map(BookFingerprint::normalizeText)
                    .anyMatch(c -> !c.isEmpty() && have.contains(c));
        }
        return true;
    }

    private static String iso3(String code) {
        String c = code.trim().toLowerCase(Locale.ROOT);
        try {
            return c.length() == 2 ? Locale.of(c).getISO3Language() : c;
        } catch (MissingResourceException ex) {
            return c;
        }
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
        SearchResponse found = search(
                query,
                SearchMode.HYBRID,
                limit,
                new Filters(
                        plan.language(), plan.categories() == null ? List.of() : plan.categories(), plan.maxPages()));
        return new DiscoveryResponse(plan, found.results(), found.degraded(), found.providers());
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
        return books.lexicalSearch(query, limit);
    }

    private List<Book> semantic(String query, int limit) {
        var store = vectors.store();
        if (store.isEmpty()) return List.of();
        List<Document> docs = store.get()
                .similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .similarityThreshold(properties.semanticThreshold())
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
