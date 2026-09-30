"use client";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { useSignedIn } from "@/lib/auth";
import { BookCard } from "@/components/BookCard";
import { ErrorNote } from "@/components/ErrorNote";
import { RagAnswerView } from "@/components/RagAnswerView";
import { ProgressPanel } from "@/components/ProgressPanel";
import { FavoriteToggle, Rating, StatusSelect, type LibraryPatch } from "@/components/LibraryControls";
import type { Book, DocumentItem, LibraryItem, Progress, RagAnswer, SummaryType } from "@/types/api";

const SUMMARY_TYPES: { value: SummaryType; label: string }[] = [
  { value: "TLDR", label: "TL;DR" },
  { value: "SHORT", label: "Short" },
  { value: "FULL", label: "Full" },
  { value: "TAKEAWAYS", label: "Key takeaways" },
];

export default function BookPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const signedIn = useSignedIn();
  const q = useQuery({ queryKey: ["book", id], queryFn: () => api<Book>(`/api/books/${id}`) });
  const similar = useQuery({
    queryKey: ["similar", id],
    queryFn: () => api<Book[]>(`/api/books/${id}/similar?limit=4`),
    enabled: !!q.data,
  });
  const shelf = useQuery({
    queryKey: ["library", ""],
    queryFn: () => api<LibraryItem[]>("/api/library"),
    enabled: signedIn,
  });
  const item = shelf.data?.find((x) => x.book.id === id);
  const progressQ = useQuery({
    queryKey: ["progress", id],
    enabled: signedIn,
    queryFn: async () => {
      try {
        return await api<Progress>(`/api/reading/${id}`);
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
  });
  const docs = useQuery({
    queryKey: ["documents"],
    queryFn: () => api<DocumentItem[]>("/api/documents"),
    enabled: signedIn,
    refetchInterval: (q) =>
      q.state.data?.some((d) => d.status === "STORED" || d.status === "PROCESSING") ? 4000 : false,
  });
  const bookDocs = docs.data?.filter((d) => d.bookId === id) ?? [];

  const [summaryType, setSummaryType] = useState<SummaryType>("SHORT");
  const [summary, setSummary] = useState("");
  const [question, setQuestion] = useState("");
  const [rag, setRag] = useState<RagAnswer>();

  const refreshShelf = () => {
    qc.invalidateQueries({ queryKey: ["library"] });
    qc.invalidateQueries({ queryKey: ["progress", id] });
    qc.invalidateQueries({ queryKey: ["dashboard"] });
  };
  const shelve = useMutation({
    mutationFn: (patch: LibraryPatch) =>
      api<LibraryItem>(`/api/library/books/${id}`, {
        method: item ? "PATCH" : "POST",
        body: JSON.stringify(patch),
      }),
    onSuccess: refreshShelf,
  });
  const summarize = useMutation({
    mutationFn: () =>
      api<{ answer: string }>(`/api/books/${id}/summary?type=${summaryType}`, { method: "POST" }),
    onSuccess: (r) => setSummary(r.answer),
  });
  const chat = useMutation({
    mutationFn: () =>
      api<RagAnswer>(`/api/books/${id}/chat`, { method: "POST", body: JSON.stringify({ question }) }),
    onSuccess: setRag,
  });
  const upload = useMutation({
    mutationFn: (f: File) => {
      const form = new FormData();
      form.append("file", f);
      return api<DocumentItem>(`/api/documents?bookId=${id}`, { method: "POST", body: form });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["documents"] }),
  });

  if (q.error)
    return (
      <main className="shell">
        <ErrorNote error={q.error} />
      </main>
    );
  const b = q.data;
  if (!b)
    return (
      <main className="shell">
        <p>Loading…</p>
      </main>
    );
  return (
    <main className="shell">
      <div className="bookdetail">
        <div className="cover">
          {b.coverUrl ? <img src={b.coverUrl} alt="" /> : <span>{b.title[0]}</span>}
        </div>
        <div>
          <p className="eyebrow">{b.categories?.join(" · ") || "Book"}</p>
          <h1 style={{ fontSize: 50, letterSpacing: "-.05em", margin: "8px 0" }}>{b.title}</h1>
          <p className="lede">
            {b.authors.join(", ") || "Unknown author"} · {b.publishedYear ?? "Unknown year"} ·{" "}
            {b.pageCount ?? "?"} pages
          </p>
          <p className="muted" style={{ lineHeight: 1.65 }}>
            {b.description ?? "No catalog description available."}
          </p>
          <div className="actions">
            {signedIn &&
              (item ? (
                <>
                  <StatusSelect
                    value={item.status}
                    disabled={shelve.isPending}
                    onChange={(s) => shelve.mutate({ status: s })}
                  />
                  <FavoriteToggle value={item.favorite} onChange={(v) => shelve.mutate({ favorite: v })} />
                  <Rating value={item.rating} onChange={(v) => shelve.mutate({ rating: v })} />
                </>
              ) : (
                <button
                  className="btn"
                  disabled={shelve.isPending}
                  onClick={() => shelve.mutate({ status: "WANT_TO_READ" })}
                >
                  + Want to read
                </button>
              ))}
          </div>
          <div className="row" style={{ marginTop: 16 }}>
            <select
              aria-label="Summary type"
              className="select"
              style={{ maxWidth: 170 }}
              value={summaryType}
              onChange={(e) => setSummaryType(e.target.value as SummaryType)}
            >
              {SUMMARY_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
            <button
              className="btn secondary"
              disabled={!signedIn || summarize.isPending}
              onClick={() => summarize.mutate()}
            >
              {summarize.isPending ? "Generating…" : "AI summary"}
            </button>
          </div>
          <ErrorNote error={shelve.error ?? summarize.error} />
          {summary && (
            <div className="panel" style={{ marginTop: 22 }}>
              <p className="eyebrow">Catalog-grounded summary</p>
              <p style={{ whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{summary}</p>
            </div>
          )}
        </div>
      </div>
      {signedIn && progressQ.isFetched && (
        <ProgressPanel
          key={progressQ.dataUpdatedAt}
          bookId={id}
          pageCount={b.pageCount}
          progress={progressQ.data}
          onSaved={(p) => {
            qc.setQueryData(["progress", id], p);
            qc.invalidateQueries({ queryKey: ["dashboard"] });
          }}
        />
      )}
      {signedIn && (
        <div className="panel" style={{ marginTop: 28 }}>
          <p className="eyebrow">Chat with this book</p>
          <p className="muted">
            Upload only content you are allowed to use. Retrieval is filtered by your user ID and this book
            ID.
          </p>
          <input
            type="file"
            aria-label="Upload a document for this book"
            accept=".pdf,.epub,.txt,.md,.markdown"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) upload.mutate(f);
              e.target.value = "";
            }}
          />
          {upload.isPending && <p className="muted">Uploading…</p>}
          <ErrorNote error={upload.error} />
          {bookDocs.length > 0 && (
            <div className="doclist" style={{ marginTop: 12 }}>
              {bookDocs.map((d) => (
                <div className="docrow" key={d.id}>
                  <span>{d.originalName}</span>
                  <span className="badge">
                    {d.status}
                    {d.status === "READY" ? ` · ${d.chunkCount} chunks` : ""}
                  </span>
                </div>
              ))}
            </div>
          )}
          {bookDocs.some((d) => d.status === "FAILED") && (
            <p className="muted" style={{ fontSize: 13 }}>
              {bookDocs.find((d) => d.status === "FAILED")?.errorMessage}
            </p>
          )}
          <form
            className="searchbox"
            onSubmit={(e) => {
              e.preventDefault();
              if (question.trim()) chat.mutate();
            }}
          >
            <input
              className="input"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="What does the source say about...?"
            />
            <button className="btn" disabled={chat.isPending || !bookDocs.some((d) => d.status === "READY")}>
              {chat.isPending ? "Retrieving…" : "Ask"}
            </button>
          </form>
          <ErrorNote error={chat.error} />
          {rag && <RagAnswerView answer={rag} />}
        </div>
      )}
      {similar.data?.length ? (
        <section style={{ marginTop: 38 }}>
          <div className="sectionhead">
            <h2>Similar books</h2>
          </div>
          <div className="grid">
            {similar.data.map((x) => (
              <BookCard key={x.id} book={x} />
            ))}
          </div>
        </section>
      ) : null}
    </main>
  );
}
