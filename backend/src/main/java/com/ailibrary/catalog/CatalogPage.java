package com.ailibrary.catalog;

import java.util.List;

public record CatalogPage(List<CatalogBook> items, long total) {}
