package com.ailibrary.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class GroundedAnswerRulesTest {
    private static RagAnswer.RagSource source(String label) {
        return new RagAnswer.RagSource(label, "doc.md", null, 0, 0.9, "snippet");
    }

    @Test
    void unknownCitationsAreRemovedAndKnownOnesKept() {
        String cleaned = GroundedAnswerService.checkCitations(
                "Ports isolate the core [S1] and adapters [S7] too.", List.of(source("S1"), source("S2")));
        assertThat(cleaned).isEqualTo("Ports isolate the core [S1] and adapters too.");
    }

    @Test
    void answersWithoutCitationsGetASourcesLine() {
        assertThat(GroundedAnswerService.checkCitations("Plain answer.", List.of(source("S1"), source("S2"))))
                .endsWith("Sources: [S1], [S2]");
        assertThat(GroundedAnswerService.checkCitations("No context.", List.of()))
                .isEqualTo("No context.");
    }

    @Test
    void contextIsCappedKeepingHighestScoringChunks() {
        Document best = Document.builder()
                .id("a")
                .text("x".repeat(3000))
                .score(0.9)
                .metadata(Map.of())
                .build();
        Document mid = Document.builder()
                .id("b")
                .text("y".repeat(3000))
                .score(0.5)
                .metadata(Map.of())
                .build();
        Document low = Document.builder()
                .id("c")
                .text("z".repeat(3000))
                .score(0.1)
                .metadata(Map.of())
                .build();
        // 2,000 tokens * 4 chars = 8,000 chars: two chunks fit.
        assertThat(GroundedAnswerService.capContext(List.of(low, best, mid), 2000))
                .extracting(Document::getId)
                .containsExactly("a", "b");
    }
}
