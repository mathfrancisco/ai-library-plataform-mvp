package com.ailibrary.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic bag-of-words embedding for retrieval tests; no provider or network needed. */
public class HashingEmbeddingModel implements EmbeddingModel {
    private final int dimensions;

    public HashingEmbeddingModel() {
        this(256);
    }

    public HashingEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) out.add(new Embedding(vector(request.getInstructions().get(i), dimensions), i));
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText(), dimensions);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    static float[] vector(String text, int dimensions) {
        float[] v = new float[dimensions];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 3) continue;
            v[Math.floorMod(token.hashCode(), dimensions)] += 1;
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }
}
