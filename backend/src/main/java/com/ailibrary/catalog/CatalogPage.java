package com.ailibrary.catalog;

import java.util.List;

/**
 * One merged page. {@code total} is approximate (sum of provider totals, duplicates included); {@code providers}
 * reports which providers answered so the UI can say e.g. "Google Books unavailable".
 */
public record CatalogPage(List<CatalogBook> items, long total, List<ProviderStatus> providers) {
    public record ProviderStatus(String name, boolean ok) {}

    public CatalogPage(List<CatalogBook> items, long total) {
        this(items, total, List.of());
    }

    public boolean complete() {
        return providers.stream().allMatch(ProviderStatus::ok);
    }
}
