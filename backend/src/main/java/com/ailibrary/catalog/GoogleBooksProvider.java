package com.ailibrary.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GoogleBooksProvider implements BookCatalogProvider {
    private final RestClient client;
    private final String apiKey;

    public GoogleBooksProvider(RestClient.Builder builder, CatalogProperties properties) {
        this.apiKey = properties.googleBooks().apiKey();
        this.client = builder.baseUrl(properties.googleBooks().baseUrl()).build();
    }

    @Override public String providerName() { return "google-books"; }
    @Override public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public CatalogPage search(String query, int page, int size) {
        if (!enabled()) return new CatalogPage(List.of(), 0);
        int max = Math.min(size, 40);
        int start = Math.max(0, (Math.max(1, page) - 1) * max);
        JsonNode root = client.get().uri(uri -> uri.path("/volumes")
                        .queryParam("q", query)
                        .queryParam("startIndex", start)
                        .queryParam("maxResults", max)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve().body(JsonNode.class);
        if (root == null) return new CatalogPage(List.of(), 0);
        List<CatalogBook> items = new ArrayList<>();
        root.path("items").forEach(n -> items.add(map(n)));
        return new CatalogPage(items, root.path("totalItems").asLong(items.size()));
    }

    @Override
    public Optional<CatalogBook> get(String externalId) {
        if (!enabled()) return Optional.empty();
        JsonNode node = client.get().uri(uri -> uri.path("/volumes/{id}").queryParam("key", apiKey).build(externalId))
                .retrieve().body(JsonNode.class);
        return Optional.ofNullable(node).map(this::map);
    }

    private CatalogBook map(JsonNode item) {
        JsonNode info = item.path("volumeInfo");
        String isbn13 = null, isbn10 = null;
        for (JsonNode id : info.path("industryIdentifiers")) {
            String type = id.path("type").asText();
            if ("ISBN_13".equals(type)) isbn13 = id.path("identifier").asText(null);
            if ("ISBN_10".equals(type)) isbn10 = id.path("identifier").asText(null);
        }
        String published = info.path("publishedDate").asText(null);
        Integer year = null;
        if (published != null && published.length() >= 4) {
            try { year = Integer.valueOf(published.substring(0, 4)); } catch (NumberFormatException ignored) {}
        }
        String cover = info.path("imageLinks").path("thumbnail").asText(null);
        return new CatalogBook(
                providerName(), item.path("id").asText(), info.path("title").asText("Untitled"), info.path("subtitle").asText(null),
                strings(info.path("authors")), isbn13, isbn10, info.path("description").asText(null), strings(info.path("categories")),
                info.path("language").asText(null), info.path("publisher").asText(null), year,
                info.hasNonNull("pageCount") ? info.get("pageCount").asInt() : null,
                cover, false, info.path("infoLink").asText(null)
        );
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(v -> { if (v.isTextual()) values.add(v.asText()); });
        return values;
    }
}
