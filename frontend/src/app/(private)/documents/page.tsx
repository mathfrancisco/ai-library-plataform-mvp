"use client";
import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, errorMessage } from "@/lib/api";
import { useDocuments, useUploadDocument } from "@/lib/hooks";
import { formatSize } from "@/lib/files";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { ErrorState } from "@/components/ErrorState";
import { FileUpload } from "@/components/FileUpload";
import { RagAnswerView } from "@/components/RagAnswerView";
import { useToast } from "@/components/Toast";
import type { DocumentItem, RagAnswer } from "@/types/api";

function StatusBadge({ d }: { d: DocumentItem }) {
  return (
    <span className={`badge status-${d.status.toLowerCase()}`}>
      {d.status === "STORED" ? "QUEUED" : d.status}
      {d.status === "READY" ? ` · ${d.chunkCount} chunk${d.chunkCount === 1 ? "" : "s"}` : ""}
    </span>
  );
}

function ChatPanel({ doc }: { doc: DocumentItem }) {
  const [question, setQuestion] = useState("");
  const ask = useMutation({
    mutationFn: () =>
      api<RagAnswer>(`/api/documents/${doc.id}/chat`, { method: "POST", body: JSON.stringify({ question }) }),
  });
  const ready = doc.status === "READY";
  return (
    <section className="panel" aria-labelledby="ask-title">
      <p className="eyebrow" id="ask-title">
        Ask {doc.originalName}
      </p>
      {!ready && doc.status !== "FAILED" && <p className="muted">Indexing… this updates automatically.</p>}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (question.trim()) ask.mutate();
        }}
      >
        <textarea
          className="textarea"
          aria-label="Question"
          maxLength={1000}
          value={question}
          disabled={!ready}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="What are the main arguments in this document?"
        />
        <button className="btn" disabled={ask.isPending || !question.trim() || !ready}>
          {ask.isPending ? "Retrieving…" : "Ask with RAG"}
        </button>
      </form>
      <ErrorState error={ask.error} />
      {ask.data && <RagAnswerView answer={ask.data} />}
    </section>
  );
}

export default function Documents() {
  const qc = useQueryClient();
  const toast = useToast();
  const docs = useDocuments();
  const upload = useUploadDocument();
  const [selected, setSelected] = useState<string>();
  const [toDelete, setToDelete] = useState<DocumentItem>();
  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/documents/${id}`, { method: "DELETE" }),
    onSuccess: (_, id) => {
      if (selected === id) setSelected(undefined);
      setToDelete(undefined);
      qc.invalidateQueries({ queryKey: ["documents"] });
      toast({ kind: "success", text: "Document deleted" });
    },
    onError: (e) => toast({ kind: "error", text: errorMessage(e) }),
  });
  const retry = useMutation({
    mutationFn: (id: string) => api<DocumentItem>(`/api/documents/${id}/reingest`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["documents"] }),
    onError: (e) => toast({ kind: "error", text: errorMessage(e) }),
  });
  const current = docs.data?.find((d) => d.id === selected);

  return (
    <main className="shell stack">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Grounded RAG</p>
          <h1>Documents</h1>
        </div>
        <span className="badge">PDF · EPUB · TXT · Markdown</span>
      </div>
      <FileUpload
        busy={upload.isPending}
        hint="Private chunks are isolated by your account and document. PDF, EPUB, TXT or Markdown, up to 25 MB."
        onFile={(file) => upload.mutate({ file }, { onSuccess: (d) => setSelected(d.id) })}
      />
      <ErrorState
        error={upload.error ?? docs.error}
        onRetry={docs.error ? () => docs.refetch() : undefined}
      />
      <section className="panel">
        <h2>Your documents</h2>
        {docs.isSuccess && !docs.data.length && <p className="muted">No documents yet.</p>}
        {!!docs.data?.length && (
          <div className="tablewrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Size</th>
                  <th>Status</th>
                  <th>Uploaded</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {docs.data.map((d) => (
                  <tr key={d.id} className={d.id === selected ? "selected" : ""}>
                    <td>
                      <button className="linkbtn" onClick={() => setSelected(d.id)}>
                        {d.originalName}
                      </button>
                      {d.status === "FAILED" && <div className="small errortext">{d.errorMessage}</div>}
                    </td>
                    <td>{formatSize(d.sizeBytes)}</td>
                    <td>
                      <StatusBadge d={d} />
                    </td>
                    <td>{new Date(d.createdAt).toLocaleDateString()}</td>
                    <td className="row">
                      {d.status === "FAILED" && (
                        <button
                          className="btn secondary small"
                          disabled={retry.isPending}
                          onClick={() => retry.mutate(d.id)}
                        >
                          Retry
                        </button>
                      )}
                      <button className="btn secondary small" onClick={() => setToDelete(d)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {current && <ChatPanel key={current.id} doc={current} />}
      <ConfirmDialog
        open={!!toDelete}
        title="Delete document?"
        body={`“${toDelete?.originalName}” and its indexed chunks will be removed permanently.`}
        confirmLabel="Delete"
        busy={remove.isPending}
        onCancel={() => setToDelete(undefined)}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
      />
    </main>
  );
}
