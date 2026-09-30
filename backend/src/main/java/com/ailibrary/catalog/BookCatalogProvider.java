package com.ailibrary.catalog;

import java.util.Optional;

public interface BookCatalogProvider {
    String providerName();

    default boolean enabled() {
        return true;
    }

    CatalogPage search(String query, int page, int size);

    Optional<CatalogBook> get(String externalId);
}
