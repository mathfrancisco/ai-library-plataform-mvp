package com.ailibrary.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.catalog")
public record CatalogProperties(OpenLibrary openLibrary, GoogleBooks googleBooks) {
    public record OpenLibrary(String userAgent, String baseUrl) {}

    public record GoogleBooks(String apiKey, String baseUrl) {}
}
