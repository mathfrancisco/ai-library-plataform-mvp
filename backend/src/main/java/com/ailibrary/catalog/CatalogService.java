package com.ailibrary.catalog;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.domain.ExternalBookReference;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.repository.ExternalBookReferenceRepository;
import com.ailibrary.book.service.BookDeduplicator;
import com.ailibrary.book.service.BookFingerprint;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.book.service.BookSavedEvent;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.error.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final int MAX_CACHED_PAGES = 1000;

    private final List<BookCatalogProvider> providers;
    private final BookRepository books;
    private final ExternalBookReferenceRepository refs;
    private final BookDeduplicator deduplicator;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Map<String, CachedPage> searchCache = new ConcurrentHashMap<>();

    public CatalogService(
            List<BookCatalogProvider> providers,
            BookRepository books,
            ExternalBookReferenceRepository refs,
            BookDeduplicator deduplicator,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactions) {
        this.providers = providers;
        this.books = books;
        this.refs = refs;
        this.deduplicator = deduplicator;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Results are cached for 5 minutes only when every enabled provider answered (SPEC-04 §12.4). */
    public CatalogPage search(String query, int page, int size) {
        String cacheKey = query.trim().toLowerCase(Locale.ROOT) + "|" + page + "|" + size;
        CachedPage cached = searchCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.page();
        List<CatalogPage.ProviderStatus> statuses = new ArrayList<>();
        List<CatalogPage> pages = new ArrayList<>();
        for (BookCatalogProvider provider : providers) {
            if (!provider.enabled()) continue;
            try {
                pages.add(provider.search(query, page, size));
                statuses.add(new CatalogPage.ProviderStatus(provider.providerName(), true));
            } catch (RuntimeException ex) {
                // Provider failure must not take the whole federated search down.
                log.warn("Catalog provider {} failed: {}", provider.providerName(), ex.toString());
                statuses.add(new CatalogPage.ProviderStatus(provider.providerName(), false));
            }
        }
        CatalogPage merged = merge(pages, size);
        CatalogPage result = new CatalogPage(merged.items(), merged.total(), List.copyOf(statuses));
        if (result.complete()) {
            if (searchCache.size() > MAX_CACHED_PAGES) searchCache.clear();
            searchCache.put(cacheKey, new CachedPage(result, Instant.now().plus(5, ChronoUnit.MINUTES)));
        }
        return result;
    }

    public List<String> providerNames() {
        return providers.stream().map(BookCatalogProvider::providerName).toList();
    }

    /** Provider order is priority order; later duplicates (same fingerprint) are dropped. */
    static CatalogPage merge(List<CatalogPage> pages, int size) {
        LinkedHashMap<String, CatalogBook> merged = new LinkedHashMap<>();
        long total = 0;
        for (CatalogPage page : pages) {
            total += page.total();
            for (CatalogBook book : page.items()) merged.putIfAbsent(fingerprint(book), book);
        }
        return new CatalogPage(merged.values().stream().limit(size).toList(), total);
    }

    static String fingerprint(CatalogBook book) {
        return BookFingerprint.of(book.isbn13(), book.isbn10(), book.title(), book.authors());
    }

    private record CachedPage(CatalogPage page, Instant expiresAt) {}

    /**
     * Imports one external record. The provider call runs outside any transaction (no network I/O while holding a
     * connection); the insert runs in a short transaction, and a lost race re-reads in a fresh one.
     */
    public BookView importBook(String providerName, String externalId) {
        BookCatalogProvider provider = providers.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        ErrorCode.VALIDATION_ERROR, "provider: must be one of " + providerNames()));
        Optional<BookView> known = tx.execute(s -> existingImport(provider.providerName(), externalId));
        if (known.isPresent()) return known.get();
        if (!provider.enabled())
            throw new BadRequestException(ErrorCode.PROVIDER_UNAVAILABLE, "Catalog provider is not available");
        CatalogBook source = provider.get(externalId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EXTERNAL_BOOK_NOT_FOUND, "External book not found"));
        try {
            return tx.execute(s -> save(source));
        } catch (DataIntegrityViolationException concurrentImport) {
            return tx.execute(s -> existingImport(source.provider(), source.externalId()))
                    .orElseThrow(() -> concurrentImport);
        }
    }

    private Optional<BookView> existingImport(String provider, String externalId) {
        return refs.findByProviderAndExternalId(provider, externalId)
                .flatMap(ref -> books.findById(ref.getBookId()))
                .map(BookMapper::toView);
    }

    private BookView save(CatalogBook source) {
        Optional<Book> existing = findExisting(source);
        Book book = existing.orElseGet(() -> books.save(new Book(
                BookFingerprint.isbn13(source.isbn13()),
                BookFingerprint.isbn10(source.isbn10()),
                source.title(),
                source.subtitle(),
                BookMapper.join(source.authors()),
                BookMapper.join(source.categories()),
                source.description(),
                source.language(),
                source.publisher(),
                source.publishedYear(),
                source.pageCount(),
                source.coverUrl(),
                source.publicDomain())));
        refs.saveAndFlush(
                new ExternalBookReference(book.getId(), source.provider(), source.externalId(), source.sourceUrl()));
        if (existing.isEmpty()) events.publishEvent(new BookSavedEvent(book.getId()));
        return BookMapper.toView(book);
    }

    Optional<Book> findExisting(CatalogBook source) {
        return deduplicator.findExisting(source.isbn13(), source.isbn10(), source.title(), source.authors());
    }
}
