package com.ailibrary.rag;

import java.util.List;
import java.util.UUID;

public record RagAnswer(String answer, List<RagSource> sources) {
    public record RagSource(
            String label, String source, UUID documentId, Integer chunkIndex, Double score, String snippet) {}
}
