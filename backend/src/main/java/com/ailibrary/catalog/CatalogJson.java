package com.ailibrary.catalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/** Small JSON helpers for provider adapters; bodies are read as String and parsed here. */
final class CatalogJson {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private CatalogJson() {}

    static JsonNode parse(String body) {
        return body == null || body.isBlank() ? MAPPER.missingNode() : MAPPER.readTree(body);
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) return null;
        String text = value.asString(null);
        return text == null || text.isBlank() ? null : text;
    }

    static Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).canConvertToInt() ? node.get(field).asInt() : null;
    }

    static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode v : node) {
            if (v.isString() && !v.asString().isBlank()) values.add(v.asString());
        }
        return values;
    }
}
