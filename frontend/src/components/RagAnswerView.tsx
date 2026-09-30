import type { RagAnswer } from "@/types/api";

/** Answers and snippets are rendered as plain text only (no HTML), since they come from an LLM and user files. */
export function RagAnswerView({ answer }: { answer: RagAnswer }) {
  return (
    <div className="mt-5">
      <p className="prewrap">{answer.answer}</p>
      {answer.sources.length > 0 && <p className="eyebrow mt-4">Sources</p>}
      {answer.sources.map((s) => (
        <div className="panel compact" key={s.label}>
          <b>
            [{s.label}] {s.source}
          </b>
          {s.chunkIndex != null && <span className="muted"> · chunk {s.chunkIndex}</span>}
          {s.score != null && <span className="muted"> · relevance {Math.round(s.score * 100)}%</span>}
          <p className="muted">
            {s.snippet}
            {s.snippet.length >= 280 ? "…" : ""}
          </p>
        </div>
      ))}
    </div>
  );
}
