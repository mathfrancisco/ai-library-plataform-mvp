package com.ailibrary.catalog;

import static com.ailibrary.catalog.CatalogJson.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class GoogleBooksProvider implements BookCatalogProvider {
    static final String NAME = "google-books";
    private final RestClient client;
    private final String apiKey;

    public GoogleBooksProvider(RestClient.Builder builder, CatalogProperties properties) {
        this.apiKey = properties.googleBooks().apiKey();
        this.client = builder.baseUrl(properties.googleBooks().baseUrl()).build();
    }

    @Override
    public String providerName() {
        return NAME;
    }

    @Override
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public CatalogPage search(String query, int page, int size) {
        if (!enabled()) return new CatalogPage(List.of(), 0);
        int max = Math.min(size, 40);
        int start = Math.max(0, (Math.max(1, page) - 1) * max);
        String body = client.get()
                .uri(uri -> uri.path("/volumes")
                        .queryParam("q", query)
                        .queryParam("startIndex", start)
                        .queryParam("maxResults", max)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(String.class);
        return mapSearch(parse(body));
    }

    @Override
    public Optional<CatalogBook> get(String externalId) {
        if (!enabled() || externalId == null || !externalId.matches("[A-Za-z0-9_-]{1,64}")) return Optional.empty();
        try {
            String body = client.get()
                    .uri(uri ->
                            uri.path("/volumes/{id}").queryParam("key", apiKey).build(externalId))
                    .retrieve()
                    .body(String.class);
            JsonNode node = parse(body);
            return node.isMissingNode() ? Optional.empty() : Optional.of(map(node));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }

    static CatalogPage mapSearch(JsonNode root) {
        List<CatalogBook> items = new ArrayList<>();
        for (JsonNode n : root.path("items")) items.add(map(n));
        return new CatalogPage(items, root.path("totalItems").asLong(items.size()));
    }

    static CatalogBook map(JsonNode item) {
        JsonNode info = item.path("volumeInfo");
        String isbn13 = null, isbn10 = null;
        for (JsonNode id : info.path("industryIdentifiers")) {
            String type = text(id, "type");
            if ("ISBN_13".equals(type)) isbn13 = text(id, "identifier");
            if ("ISBN_10".equals(type)) isbn10 = text(id, "identifier");
        }
        String published = text(info, "publishedDate");
        Integer year = null;
        if (published != null && published.length() >= 4) {
            try {
                year = Integer.valueOf(published.substring(0, 4));
            } catch (NumberFormatException ignored) {
            }
        }
        String cover = text(info.path("imageLinks"), "thumbnail");
        if (cover != null && cover.startsWith("http://")) cover = "https://" + cover.substring("http://".length());
        boolean publicDomain = item.path("accessInfo").path("publicDomain").asBoolean(false);
        return new CatalogBook(
                NAME,
                text(item, "id"),
                Optional.ofNullable(text(info, "title")).orElse("Untitled"),
                text(info, "subtitle"),
                strings(info.path("authors")),
                isbn13,
                isbn10,
                text(info, "description"),
                strings(info.path("categories")),
                text(info, "language"),
                text(info, "publisher"),
                year,
                integer(info, "pageCount"),
                cover,
                publicDomain,
                text(info, "infoLink"));
    }
}
