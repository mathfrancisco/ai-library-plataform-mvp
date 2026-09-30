"use client";
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useSignedIn } from "@/lib/auth";
import { ErrorState } from "@/components/ErrorState";
import type { SummaryType } from "@/types/api";

const TYPES: { value: SummaryType; label: string }[] = [
  { value: "TLDR", label: "TL;DR" },
  { value: "SHORT", label: "Short" },
  { value: "TAKEAWAYS", label: "Takeaways" },
];

export function SummaryTabs({ bookId, hasDescription }: { bookId: string; hasDescription: boolean }) {
  const signedIn = useSignedIn();
  const [type, setType] = useState<SummaryType>("SHORT");
  const [results, setResults] = useState<Partial<Record<SummaryType, string>>>({});
  const summarize = useMutation({
    mutationFn: (t: SummaryType) =>
      api<{ answer: string }>(`/api/books/${bookId}/summary?type=${t}`, { method: "POST" }),
    onSuccess: (r, t) => setResults((v) => ({ ...v, [t]: r.answer })),
  });
  if (!signedIn || !hasDescription) return null;
  return (
    <section className="panel" aria-labelledby="summary-title">
      <div className="row between">
        <p className="eyebrow" id="summary-title">
          AI summary · based on the catalog description
        </p>
        <div role="tablist" aria-label="Summary type" className="tabs">
          {TYPES.map((t) => (
            <button
              key={t.value}
              role="tab"
              aria-selected={type === t.value}
              className={type === t.value ? "active" : ""}
              onClick={() => setType(t.value)}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>
      {results[type] ? (
        <p className="prewrap" role="tabpanel">
          {results[type]}
        </p>
      ) : (
        <button
          className="btn secondary"
          disabled={summarize.isPending}
          onClick={() => summarize.mutate(type)}
        >
          {summarize.isPending ? "Generating…" : "Generate summary"}
        </button>
      )}
      <ErrorState error={summarize.error} />
    </section>
  );
}
