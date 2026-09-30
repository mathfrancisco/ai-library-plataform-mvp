package com.ailibrary.catalog;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.ailibrary.catalog.CatalogJson.*;

@Component
@EnableConfigurationProperties(CatalogProperties.class)
public class OpenLibraryProvider implements BookCatalogProvider {
    static final String NAME = "open-library";
    static final String SEARCH_FIELDS = "key,title,subtitle,author_name,isbn,first_publish_year,cover_i,language,publisher,subject,number_of_pages_median,public_scan_b";
    private static final Pattern WORK_KEY = Pattern.compile("^/works/OL\\d+W$");

    private final RestClient client;
    // Identified clients may send 3 req/s; stay slightly below.
    private final ProviderRequestGate gate = new ProviderRequestGate(350);

    public OpenLibraryProvider(RestClient.Builder builder, CatalogProperties properties) {
        this.client = builder
                .baseUrl(properties.openLibrary().baseUrl())
                .defaultHeader("User-Agent", properties.openLibrary().userAgent())
                .build();
    }

    @Override public String providerName() { return NAME; }

    @Override
    public CatalogPage search(String query, int page, int size) {
        gate.awaitTurn();
        String body = client.get().uri(uri -> uri.path("/search.json")
                        .queryParam("q", query)
                        .queryParam("page", Math.max(1, page))
                        .queryParam("limit", Math.min(size, 50))
                        .queryParam("fields", SEARCH_FIELDS)
                        .build())
                .retrieve().body(String.class);
        return mapSearch(parse(body));
    }

    @Override
    public Optional<CatalogBook> get(String externalId) {
        String key = workKey(externalId);
        if (key == null) return Optional.empty();
        Optional<CatalogBook> found = search("key:" + key, 1, 1).items().stream().findFirst();
        return found.map(book -> {
            try {
                gate.awaitTurn();
                String work = client.get().uri(key + ".json").retrieve().body(String.class);
                return withWorkDetails(book, parse(work));
            } catch (RestClientException ex) {
                // The search document is still a valid, if thinner, catalog record.
                return book;
            }
        });
    }

    static String workKey(String externalId) {
        if (externalId == null) return null;
        String key = externalId.trim();
        if (!key.startsWith("/")) key = "/works/" + key;
        return WORK_KEY.matcher(key).matches() ? key : null;
    }

    static CatalogPage mapSearch(JsonNode root) {
        List<CatalogBook> items = new ArrayList<>();
        for (JsonNode doc : root.path("docs")) items.add(map(doc));
        return new CatalogPage(items, root.path("numFound").asLong(items.size()));
    }

    static CatalogBook map(JsonNode node) {
        String key = text(node, "key");
        List<String> isbn = strings(node.path("isbn"));
        String isbn13 = isbn.stream().filter(v -> v.length() == 13).findFirst().orElse(null);
        String isbn10 = isbn.stream().filter(v -> v.length() == 10).findFirst().orElse(null);
        Integer coverId = integer(node, "cover_i");
        String cover = coverId == null ? null : "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg";
        return new CatalogBook(
                NAME, key, Optional.ofNullable(text(node, "title")).orElse("Untitled"), text(node, "subtitle"),
                strings(node.path("author_name")), isbn13, isbn10, null,
                strings(node.path("subject")).stream().limit(12).toList(),
                strings(node.path("language")).stream().findFirst().orElse(null),
                strings(node.path("publisher")).stream().findFirst().orElse(null),
                integer(node, "first_publish_year"), integer(node, "number_of_pages_median"),
                cover, node.path("public_scan_b").asBoolean(false), key == null ? null : "https://openlibrary.org" + key
        );
    }

    /** Work records carry the description, which search results do not. */
    static CatalogBook withWorkDetails(CatalogBook book, JsonNode work) {
        JsonNode raw = work.path("description");
        String description = raw.isObject() ? text(raw, "value") : (raw.isString() ? raw.asString() : null);
        List<String> subjects = book.categories().isEmpty() ? strings(work.path("subjects")).stream().limit(12).toList() : book.categories();
        return new CatalogBook(book.provider(), book.externalId(), book.title(), book.subtitle(), book.authors(), book.isbn13(),
                book.isbn10(), description == null ? book.description() : description.strip(), subjects, book.language(), book.publisher(),
                book.publishedYear(), book.pageCount(), book.coverUrl(), book.publicDomain(), book.sourceUrl());
    }
}
