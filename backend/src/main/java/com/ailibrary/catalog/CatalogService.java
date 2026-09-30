package com.ailibrary.catalog;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.domain.ExternalBookReference;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.repository.ExternalBookReferenceRepository;
import com.ailibrary.book.service.BookFingerprint;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.book.service.BookVectorIndexer;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.error.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final int MAX_CACHED_PAGES = 1000;

    private final List<BookCatalogProvider> providers;
    private final BookRepository books;
    private final ExternalBookReferenceRepository refs;
    private final BookVectorIndexer vectorIndexer;
    private final Map<String, CachedPage> searchCache = new ConcurrentHashMap<>();

    public CatalogService(
            List<BookCatalogProvider> providers,
            BookRepository books,
            ExternalBookReferenceRepository refs,
            BookVectorIndexer vectorIndexer) {
        this.providers = providers;
        this.books = books;
        this.refs = refs;
        this.vectorIndexer = vectorIndexer;
    }

    public CatalogPage search(String query, int page, int size) {
        String cacheKey = query.trim().toLowerCase(Locale.ROOT) + "|" + page + "|" + size;
        CachedPage cached = searchCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.page();
        CatalogPage result = merge(
                providers.stream()
                        .filter(BookCatalogProvider::enabled)
                        .map(provider -> {
                            try {
                                return provider.search(query, page, size);
                            } catch (RuntimeException ex) {
                                // Provider failure must not take the whole federated search down.
                                log.warn("Catalog provider {} failed: {}", provider.providerName(), ex.getMessage());
                                return new CatalogPage(List.of(), 0);
                            }
                        })
                        .toList(),
                size);
        if (searchCache.size() > MAX_CACHED_PAGES) searchCache.clear();
        searchCache.put(cacheKey, new CachedPage(result, Instant.now().plus(5, ChronoUnit.MINUTES)));
        return result;
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

    @Transactional
    public BookView importBook(String providerName, String externalId) {
        ExternalBookReference existingRef =
                refs.findByProviderAndExternalId(providerName, externalId).orElse(null);
        if (existingRef != null) {
            return BookMapper.toView(books.findById(existingRef.getBookId()).orElseThrow(NotFoundException::book));
        }

        BookCatalogProvider provider = providers.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(providerName) && p.enabled())
                .findFirst()
                .orElseThrow(() ->
                        new BadRequestException(ErrorCode.PROVIDER_UNAVAILABLE, "Catalog provider is not available"));
        CatalogBook source = provider.get(externalId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EXTERNAL_BOOK_NOT_FOUND, "External book not found"));

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

        refs.findByProviderAndExternalId(source.provider(), source.externalId())
                .orElseGet(() -> refs.save(new ExternalBookReference(
                        book.getId(), source.provider(), source.externalId(), source.sourceUrl())));
        if (existing.isEmpty()) vectorIndexer.index(book);
        return BookMapper.toView(book);
    }

    Optional<Book> findExisting(CatalogBook source) {
        String isbn13 = BookFingerprint.isbn13(source.isbn13());
        if (isbn13 == null) isbn13 = BookFingerprint.isbn10To13(source.isbn10());
        if (isbn13 != null) {
            Optional<Book> byIsbn = books.findByIsbn13(isbn13);
            if (byIsbn.isPresent()) return byIsbn;
        }
        String isbn10 = BookFingerprint.isbn10(source.isbn10());
        if (isbn10 != null) {
            Optional<Book> byIsbn10 = books.findByIsbn10(isbn10).stream().findFirst();
            if (byIsbn10.isPresent()) return byIsbn10;
        }
        if (source.title() == null) return Optional.empty();
        String wanted = BookFingerprint.of(null, null, source.title(), source.authors());
        return books.findTop20ByTitleIgnoreCase(source.title().trim()).stream()
                .filter(b -> BookFingerprint.of(
                                null, null, b.getTitle(), BookMapper.toView(b).authors())
                        .equals(wanted))
                .findFirst();
    }
}
