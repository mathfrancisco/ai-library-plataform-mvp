package com.ailibrary.rag;

import java.util.List;

public record RagAnswer(String answer, List<RagSource> sources) {
    public record RagSource(String label, String source, String chunkIndex, String snippet) {}
}
