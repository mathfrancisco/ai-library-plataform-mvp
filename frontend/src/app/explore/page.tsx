"use client";
import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { useSignedIn } from "@/lib/auth";
import { ErrorNote } from "@/components/ErrorNote";
import type { Book, Discovery, SearchHit } from "@/types/api";

export default function Explore() {
  const router = useRouter();
  const signedIn = useSignedIn();
  const [added, setAdded] = useState<string[]>([]);
  const [q, setQ] = useState("software architecture");
  const [submitted, setSubmitted] = useState(q);
  const [mode, setMode] = useState("HYBRID");
  const [prompt, setPrompt] = useState(
    "I want an accessible book to learn distributed systems as a Java developer",
  );
  const [discovered, setDiscovered] = useState<Discovery>();
  const search = useQuery({
    queryKey: ["search", submitted, mode],
    queryFn: () => api<SearchHit[]>(`/api/search?q=${encodeURIComponent(submitted)}&mode=${mode}`),
    enabled: !!submitted,
  });
  const discover = useMutation({
    mutationFn: () =>
      api<Discovery>("/api/search/discover", { method: "POST", body: JSON.stringify({ prompt }) }),
    onSuccess: setDiscovered,
  });
  const add = useMutation({
    mutationFn: async (h: SearchHit) => {
      let id = h.localBookId;
      if (!id) {
        const b = await api<Book>("/api/catalog/import", {
          method: "POST",
          body: JSON.stringify({ provider: h.provider, externalId: h.externalId }),
        });
        id = b.id;
      }
      if (!id) throw new Error("Book import failed");
      await api(`/api/library/books/${id}`, {
        method: "POST",
        body: JSON.stringify({ status: "WANT_TO_READ" }),
      });
      return id;
    },
    onSuccess: (id) => setAdded((v) => [...v, id]),
  });
  const open = useMutation({
    mutationFn: async (h: SearchHit) =>
      h.localBookId ??
      (
        await api<Book>("/api/catalog/import", {
          method: "POST",
          body: JSON.stringify({ provider: h.provider, externalId: h.externalId }),
        })
      ).id,
    onSuccess: (id) => {
      if (id) router.push(`/book/${id}`);
    },
  });
  const key = (h: SearchHit) => `${h.provider}-${h.externalId}-${h.localBookId}`;
  const results = discovered?.results ?? search.data ?? [];
  return (
    <main className="shell">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Hybrid discovery</p>
          <h1>Explore books</h1>
        </div>
        <div className="row">
          <span className="badge">FTS + vector + federated catalog</span>
          {signedIn && (
            <Link className="btn secondary" href="/books/new">
              + Add manually
            </Link>
          )}
        </div>
      </div>
      <form
        className="searchbox"
        onSubmit={(e) => {
          e.preventDefault();
          setDiscovered(undefined);
          setSubmitted(q);
        }}
      >
        <input
          className="input"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Title, author, ISBN or keywords"
        />
        <select
          className="select"
          style={{ maxWidth: 150 }}
          value={mode}
          onChange={(e) => setMode(e.target.value)}
        >
          <option>HYBRID</option>
          <option>LEXICAL</option>
          <option>SEMANTIC</option>
        </select>
        <button className="btn">Search</button>
      </form>
      <div className="panel" style={{ marginBottom: 28 }}>
        <p className="eyebrow">Discover with natural language</p>
        <div className="searchbox" style={{ marginBottom: 0 }}>
          <input className="input" value={prompt} onChange={(e) => setPrompt(e.target.value)} />
          <button
            className="btn secondary"
            onClick={() => discover.mutate()}
            disabled={discover.isPending || !signedIn}
            title={signedIn ? undefined : "Sign in to use AI discovery"}
          >
            {discover.isPending ? "Understanding…" : "Discover with AI"}
          </button>
        </div>
        {discovered && (
          <p className="muted">
            Interpreted as: <b>{discovered.plan.query}</b>
            {discovered.plan.categories?.length ? ` · ${discovered.plan.categories.join(", ")}` : ""}
          </p>
        )}
      </div>
      <ErrorNote error={search.error ?? discover.error ?? add.error ?? open.error} />
      {(search.isLoading || discover.isPending) && (
        <p className="muted">Searching local knowledge and catalogs…</p>
      )}
      <div className="grid">
        {results.map((h, i) => (
          <article className="bookcard" key={`${key(h)}-${i}`}>
            <button
              className="cover"
              style={{ border: 0, width: "100%", cursor: signedIn || h.localBookId ? "pointer" : "default" }}
              disabled={!signedIn && !h.localBookId}
              onClick={() => open.mutate(h)}
              aria-label={`Open ${h.title}`}
            >
              {h.coverUrl ? <img src={h.coverUrl} alt="" /> : <span>{h.title[0]}</span>}
            </button>
            <div className="bookmeta">
              <p className="eyebrow" title={h.matchedBy?.join(" + ")}>
                {h.matchType}
                {h.matchType === "HYBRID" && h.matchedBy ? ` · ${h.matchedBy.join(" + ").toLowerCase()}` : ""}
              </p>
              <h3>{h.title}</h3>
              <p>{h.authors?.join(", ")}</p>
              <div className="row">
                <span>{h.provider ?? "LOCAL"}</span>
                <span>{h.score.toFixed(4)}</span>
              </div>
              {signedIn && (
                <button
                  className="smallbtn"
                  disabled={add.isPending && add.variables === h}
                  onClick={() => add.mutate(h)}
                >
                  {add.isPending && add.variables === h
                    ? "Adding…"
                    : h.localBookId && added.includes(h.localBookId)
                      ? "✓ On your shelf"
                      : "+ Want to read"}
                </button>
              )}
            </div>
          </article>
        ))}
      </div>
    </main>
  );
}
