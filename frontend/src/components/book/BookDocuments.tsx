"use client";
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useDocuments, useUploadDocument } from "@/lib/hooks";
import { ErrorState } from "@/components/ErrorState";
import { FileUpload } from "@/components/FileUpload";
import { RagAnswerView } from "@/components/RagAnswerView";
import type { RagAnswer } from "@/types/api";

export function BookDocuments({ bookId }: { bookId: string }) {
  const docs = useDocuments();
  const upload = useUploadDocument();
  const [question, setQuestion] = useState("");
  const chat = useMutation({
    mutationFn: () =>
      api<RagAnswer>(`/api/books/${bookId}/chat`, { method: "POST", body: JSON.stringify({ question }) }),
  });
  const mine = docs.data?.filter((d) => d.bookId === bookId) ?? [];
  const ready = mine.some((d) => d.status === "READY");
  return (
    <section className="panel" aria-labelledby="book-chat">
      <p className="eyebrow" id="book-chat">
        Chat with this book
      </p>
      <p className="muted">Retrieval is filtered by your account and this book.</p>
      <FileUpload
        busy={upload.isPending}
        onFile={(file) => upload.mutate({ file, bookId })}
        label="Upload a document for this book"
      />
      <ErrorState error={upload.error} />
      {mine.length > 0 && (
        <ul className="doclist">
          {mine.map((d) => (
            <li className="docrow" key={d.id}>
              <span>{d.originalName}</span>
              <span className={`badge status-${d.status.toLowerCase()}`}>
                {d.status}
                {d.status === "READY" ? ` · ${d.chunkCount} chunk${d.chunkCount === 1 ? "" : "s"}` : ""}
              </span>
              {d.status === "FAILED" && <span className="muted small">{d.errorMessage}</span>}
            </li>
          ))}
        </ul>
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
          aria-label="Question about this book"
          maxLength={1000}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder={ready ? "What does the source say about…?" : "Upload a document first"}
          disabled={!ready}
        />
        <button className="btn" disabled={chat.isPending || !ready || !question.trim()}>
          {chat.isPending ? "Retrieving…" : "Ask"}
        </button>
      </form>
      <ErrorState error={chat.error} />
      {chat.data && <RagAnswerView answer={chat.data} />}
    </section>
  );
}
