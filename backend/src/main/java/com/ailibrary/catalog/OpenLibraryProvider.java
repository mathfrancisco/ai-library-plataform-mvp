package com.ailibrary.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
@EnableConfigurationProperties(CatalogProperties.class)
public class OpenLibraryProvider implements BookCatalogProvider {
    private final RestClient client;
    private final ProviderRequestGate gate = new ProviderRequestGate(350);

    public OpenLibraryProvider(RestClient.Builder builder, CatalogProperties properties) {
        this.client = builder
                .baseUrl(properties.openLibrary().baseUrl())
                .defaultHeader("User-Agent", properties.openLibrary().userAgent())
                .build();
    }

    @Override public String providerName() { return "open-library"; }

    @Override
    public CatalogPage search(String query, int page, int size) {
        gate.awaitTurn();
        JsonNode root = client.get().uri(uri -> uri.path("/search.json")
                        .queryParam("q", query)
                        .queryParam("page", Math.max(1, page))
                        .queryParam("limit", Math.min(size, 50))
                        .queryParam("fields", "key,title,subtitle,author_name,isbn,first_publish_year,cover_i,language,publisher,subject,number_of_pages_median")
                        .build())
                .retrieve().body(JsonNode.class);
        if (root == null) return new CatalogPage(List.of(), 0);
        List<CatalogBook> items = new ArrayList<>();
        for (JsonNode doc : root.path("docs")) items.add(map(doc));
        return new CatalogPage(items, root.path("numFound").asLong(items.size()));
    }

    @Override
    public Optional<CatalogBook> get(String externalId) {
        String id = externalId.startsWith("/") ? externalId : "/works/" + externalId;
        CatalogPage page = search("key:" + id, 1, 1);
        return page.items().stream().findFirst();
    }

    private CatalogBook map(JsonNode node) {
        String key = text(node, "key");
        List<String> isbn = strings(node.path("isbn"));
        String isbn13 = isbn.stream().filter(v -> v.length() == 13).findFirst().orElse(null);
        String isbn10 = isbn.stream().filter(v -> v.length() == 10).findFirst().orElse(null);
        Integer coverId = node.hasNonNull("cover_i") ? node.get("cover_i").asInt() : null;
        String cover = coverId == null ? null : "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg";
        return new CatalogBook(
                providerName(), key, text(node, "title"), text(node, "subtitle"), strings(node.path("author_name")),
                isbn13, isbn10, null, first(strings(node.path("subject")), 12), first(strings(node.path("language")), 1).stream().findFirst().orElse(null),
                first(strings(node.path("publisher")), 1).stream().findFirst().orElse(null),
                node.hasNonNull("first_publish_year") ? node.get("first_publish_year").asInt() : null,
                node.hasNonNull("number_of_pages_median") ? node.get("number_of_pages_median").asInt() : null,
                cover, false, key == null ? null : "https://openlibrary.org" + key
        );
    }

    private String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(v -> { if (v.isTextual() && !v.asText().isBlank()) values.add(v.asText()); });
        return values;
    }

    private List<String> first(List<String> values, int max) {
        return values.stream().limit(max).toList();
    }
}
