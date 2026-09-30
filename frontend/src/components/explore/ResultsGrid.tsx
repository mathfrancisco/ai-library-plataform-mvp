"use client";
import { BookCard } from "@/components/BookCard";
import type { SearchHit } from "@/types/api";

export const hitKey = (h: SearchHit) => h.localBookId ?? `${h.provider}:${h.externalId}`;

const MATCH_LABEL: Record<string, string> = {
  LEXICAL: "Keyword match",
  SEMANTIC: "Meaning match",
  EXTERNAL: "Catalog",
  HYBRID: "Strong match",
};

export function ResultsGrid({
  hits,
  signedIn,
  pendingKey,
  shelved,
  opening,
  onAdd,
  onOpen,
}: {
  hits: SearchHit[];
  signedIn: boolean;
  pendingKey?: string;
  shelved: Set<string>;
  opening?: string;
  onAdd: (h: SearchHit) => void;
  onOpen: (h: SearchHit) => void;
}) {
  const top = Math.max(...hits.map((h) => h.score), 0.0001);
  return (
    <div className="grid">
      {hits.map((h) => {
        const key = hitKey(h);
        const busy = pendingKey === key;
        return (
          <BookCard
            key={key}
            book={{ id: h.localBookId, title: h.title, authors: h.authors, coverUrl: h.coverUrl }}
            eyebrow={MATCH_LABEL[h.matchType] ?? h.matchType}
          >
            <div
              className="relevance"
              title={h.matchedBy?.join(" + ")}
              aria-label={`Relevance ${Math.round((h.score / top) * 100)}%`}
            >
              <span style={{ width: `${(h.score / top) * 100}%` }} />
            </div>
            <div className="row">
              {!h.localBookId && (
                <button
                  className="smallbtn secondary"
                  disabled={!signedIn || opening === key}
                  onClick={() => onOpen(h)}
                >
                  {opening === key ? "Opening…" : "Details"}
                </button>
              )}
              {signedIn &&
                (shelved.has(key) ? (
                  <span className="badge okay">In your shelf</span>
                ) : (
                  <button className="smallbtn" disabled={busy} onClick={() => onAdd(h)}>
                    {busy ? "Adding…" : "+ Want to read"}
                  </button>
                ))}
            </div>
          </BookCard>
        );
      })}
    </div>
  );
}
