"use client";
import { useState } from "react";
import type { SearchMode } from "@/types/api";

export const MODES: { value: SearchMode; label: string }[] = [
  { value: "HYBRID", label: "Smart (hybrid)" },
  { value: "LEXICAL", label: "Keyword" },
  { value: "SEMANTIC", label: "Meaning" },
];

export const SUGGESTIONS = ["software architecture", "space survival", "Brazilian literature", "Clean Code"];

export function SearchForm({
  initialQuery,
  initialMode,
  onSearch,
}: {
  initialQuery: string;
  initialMode: SearchMode;
  onSearch: (q: string, mode: SearchMode) => void;
}) {
  const [q, setQ] = useState(initialQuery);
  const [mode, setMode] = useState<SearchMode>(initialMode);
  return (
    <>
      <form
        className="searchbox"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          if (q.trim()) onSearch(q.trim(), mode);
        }}
      >
        <input
          className="input"
          aria-label="Search books"
          value={q}
          maxLength={200}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Title, author, ISBN or keywords"
        />
        <select
          aria-label="Search mode"
          className="select mode"
          value={mode}
          onChange={(e) => setMode(e.target.value as SearchMode)}
        >
          {MODES.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
        <button className="btn">Search</button>
      </form>
      {!initialQuery && (
        <div className="chips" aria-label="Suggestions">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              type="button"
              className="chip"
              onClick={() => {
                setQ(s);
                onSearch(s, mode);
              }}
            >
              {s}
            </button>
          ))}
        </div>
      )}
    </>
  );
}
