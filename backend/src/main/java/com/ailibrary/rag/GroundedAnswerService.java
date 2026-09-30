package com.ailibrary.rag;

import com.ailibrary.ai.AiFacade;
import com.ailibrary.ai.AiPromptTemplates;
import com.ailibrary.ai.ModelTier;
import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.error.ErrorCode;
import com.ailibrary.common.vector.VectorStoreAccess;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Explicit retrieve → grounded prompt → answer pipeline shared by document and book chat. */
@Service
@EnableConfigurationProperties(RagProperties.class)
public class GroundedAnswerService {
    private static final int SNIPPET_CHARS = 280;
    /** Rough English average; good enough to keep prompts under small-model context and TPM limits. */
    static final int CHARS_PER_TOKEN = 4;

    private static final Pattern CITATION = Pattern.compile("\\[(S\\d+)]");

    private final VectorStoreAccess vectors;
    private final AiFacade ai;
    private final AiPromptTemplates prompts;
    private final RagProperties properties;

    public GroundedAnswerService(
            VectorStoreAccess vectors, AiFacade ai, AiPromptTemplates prompts, RagProperties properties) {
        this.vectors = vectors;
        this.ai = ai;
        this.prompts = prompts;
        this.properties = properties;
    }

    public record Retrieval(Filter.Expression filter, int topK, double similarityThreshold) {}

    public RagProperties properties() {
        return properties;
    }

    public RagAnswer answer(
            UUID ownerId,
            String operation,
            String question,
            Retrieval retrieval,
            String defaultSourceName,
            String noContextMessage) {
        VectorStore store = vectors.store()
                .orElseThrow(() -> new ApiException(ErrorCode.VECTOR_DISABLED, "Vector search is disabled"));
        List<Document> chunks = capContext(retrieve(store, question, retrieval), properties.maxContextTokens());
        if (chunks.isEmpty()) return new RagAnswer(noContextMessage, List.of());

        StringBuilder context = new StringBuilder();
        List<RagAnswer.RagSource> sources = new ArrayList<>();
        int n = 1;
        for (Document chunk : chunks) {
            String label = "S" + n++;
            String text = Objects.toString(chunk.getText(), "");
            context.append('[').append(label).append("]\n").append(text).append("\n\n");
            sources.add(new RagAnswer.RagSource(
                    label,
                    Objects.toString(chunk.getMetadata().get("sourceName"), defaultSourceName),
                    uuid(chunk.getMetadata().get("documentId")),
                    integer(chunk.getMetadata().get("chunkIndex")),
                    chunk.getScore(),
                    text.substring(0, Math.min(SNIPPET_CHARS, text.length()))));
        }
        String answer = ai.complete(
                ownerId,
                operation,
                ModelTier.SMART,
                AiPromptTemplates.GROUNDED_SYSTEM,
                prompts.groundedQuestion(question, context.toString().trim()));
        return new RagAnswer(checkCitations(answer, sources), sources);
    }

    static List<Document> retrieve(VectorStore store, String question, Retrieval retrieval) {
        List<Document> found = store.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(retrieval.topK())
                .similarityThreshold(retrieval.similarityThreshold())
                .filterExpression(retrieval.filter())
                .build());
        return found == null ? List.of() : found;
    }

    /** Keeps the highest-scoring chunks whose combined size fits the token budget (SPEC-04 §9.3). */
    static List<Document> capContext(List<Document> chunks, int maxTokens) {
        int budget = maxTokens * CHARS_PER_TOKEN;
        List<Document> byScore = chunks.stream()
                .sorted((a, b) -> Double.compare(
                        b.getScore() == null ? 0 : b.getScore(), a.getScore() == null ? 0 : a.getScore()))
                .toList();
        List<Document> kept = new ArrayList<>();
        int used = 0;
        for (Document d : byScore) {
            int size = Objects.toString(d.getText(), "").length();
            if (used + size > budget && !kept.isEmpty()) continue;
            kept.add(d);
            used += size;
        }
        return kept;
    }

    /** Removes citations to labels that do not exist and adds a sources line when the model cited none. */
    static String checkCitations(String answer, List<RagAnswer.RagSource> sources) {
        if (answer == null) return "";
        Set<String> known = sources.stream().map(RagAnswer.RagSource::label).collect(Collectors.toSet());
        Set<String> cited = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            boolean ok = known.contains(m.group(1));
            if (ok) cited.add(m.group(1));
            m.appendReplacement(out, ok ? Matcher.quoteReplacement(m.group()) : "");
        }
        m.appendTail(out);
        String cleaned = out.toString().replaceAll(" {2,}", " ").trim();
        if (cited.isEmpty() && !sources.isEmpty()) {
            cleaned += "\n\nSources: "
                    + sources.stream().map(s -> "[" + s.label() + "]").collect(Collectors.joining(", "));
        }
        return cleaned;
    }

    private static UUID uuid(Object raw) {
        try {
            return raw == null ? null : UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Integer integer(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        try {
            return raw == null ? null : Integer.valueOf(raw.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
