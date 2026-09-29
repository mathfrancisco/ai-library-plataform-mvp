package com.ailibrary.catalog;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.domain.ExternalBookReference;
import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.repository.ExternalBookReferenceRepository;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.book.service.BookVectorIndexer;
import com.ailibrary.common.error.BadRequestException;
import com.ailibrary.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CatalogService {
    private final List<BookCatalogProvider> providers;
    private final BookRepository books;
    private final ExternalBookReferenceRepository refs;
    private final BookVectorIndexer vectorIndexer;
    private final Map<String, CachedPage> searchCache = new ConcurrentHashMap<>();

    public CatalogService(List<BookCatalogProvider> providers, BookRepository books,
                          ExternalBookReferenceRepository refs, BookVectorIndexer vectorIndexer) {
        this.providers = providers;
        this.books = books;
        this.refs = refs;
        this.vectorIndexer = vectorIndexer;
    }

    public CatalogPage search(String query, int page, int size) {
        String cacheKey = query.trim().toLowerCase(Locale.ROOT)+"|"+page+"|"+size;
        CachedPage cached = searchCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.page();
        LinkedHashMap<String, CatalogBook> merged = new LinkedHashMap<>();
        long total = 0;
        for (BookCatalogProvider provider : providers) {
            if (!provider.enabled()) continue;
            try {
                CatalogPage result = provider.search(query, page, size);
                total += result.total();
                for (CatalogBook book : result.items()) merged.putIfAbsent(fingerprint(book), book);
            } catch (RuntimeException ignored) {
                // Provider failure must not take the whole federated search down.
            }
        }
        CatalogPage result = new CatalogPage(merged.values().stream().limit(size).toList(), total);
        if (searchCache.size() > 1000) searchCache.clear();
        searchCache.put(cacheKey, new CachedPage(result, Instant.now().plus(5, ChronoUnit.MINUTES)));
        return result;
    }

    private record CachedPage(CatalogPage page, Instant expiresAt) {}

    @Transactional
    public BookView importBook(String providerName, String externalId) {
        ExternalBookReference existingRef = refs.findByProviderAndExternalId(providerName, externalId).orElse(null);
        if (existingRef != null) {
            return BookMapper.toView(books.findById(existingRef.getBookId()).orElseThrow());
        }

        BookCatalogProvider provider = providers.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(providerName) && p.enabled())
                .findFirst().orElseThrow(() -> new BadRequestException("Catalog provider is not available"));
        CatalogBook source = provider.get(externalId).orElseThrow(() -> new NotFoundException("External book not found"));

        Book book = findExisting(source).orElseGet(() -> books.save(new Book(
                cleanIsbn(source.isbn13(), 13), cleanIsbn(source.isbn10(), 10), source.title(), source.subtitle(),
                BookMapper.join(source.authors()), BookMapper.join(source.categories()), source.description(), source.language(),
                source.publisher(), source.publishedYear(), source.pageCount(), source.coverUrl(), source.publicDomain()
        )));

        refs.save(new ExternalBookReference(book.getId(), source.provider(), source.externalId(), source.sourceUrl()));
        vectorIndexer.index(book);
        return BookMapper.toView(book);
    }

    private Optional<Book> findExisting(CatalogBook source) {
        String isbn = cleanIsbn(source.isbn13(), 13);
        if (isbn != null) {
            Optional<Book> byIsbn = books.findByIsbn13(isbn);
            if (byIsbn.isPresent()) return byIsbn;
        }
        String author = source.authors() == null || source.authors().isEmpty() ? "" : source.authors().getFirst();
        return books.findFingerprint(source.title(), author);
    }

    private String cleanIsbn(String value, int length) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[^0-9Xx]", "");
        return cleaned.length() == length ? cleaned : null;
    }

    private String fingerprint(CatalogBook book) {
        if (book.isbn13() != null) return "isbn13:" + book.isbn13().replaceAll("[^0-9]", "");
        if (book.isbn10() != null) return "isbn10:" + book.isbn10().replaceAll("[^0-9Xx]", "");
        String author = book.authors() == null || book.authors().isEmpty() ? "" : book.authors().getFirst();
        return normalize(book.title()) + "::" + normalize(author);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
