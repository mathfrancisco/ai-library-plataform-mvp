"use client";
import { useState } from "react";
import { useProgress, useSaveProgress } from "@/lib/hooks";
import { ErrorState } from "@/components/ErrorState";
import { ProgressBar } from "@/components/ProgressBar";
import { useToast } from "@/components/Toast";
import type { Progress } from "@/types/api";

function Form({
  bookId,
  pageCount,
  progress,
}: {
  bookId: string;
  pageCount?: number | null;
  progress: Progress;
}) {
  const toast = useToast();
  const [page, setPage] = useState(progress.exists ? String(progress.currentPage) : "");
  const [percentage, setPercentage] = useState(progress.exists ? String(progress.percentage) : "");
  const save = useSaveProgress(bookId);
  const derived = !!pageCount && !!page;
  const shown = derived ? Math.min(100, (Number(page) / pageCount!) * 100) : Number(percentage || 0);
  return (
    <form
      className="panel"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate(
          {
            currentPage: page ? Number(page) : undefined,
            percentage: percentage && !derived ? Number(percentage) : undefined,
          },
          { onSuccess: () => toast({ kind: "success", text: "Progress saved" }) },
        );
      }}
    >
      <p className="eyebrow">Reading progress</p>
      <ProgressBar value={progress.exists ? progress.percentage : shown} label="Reading progress" />
      <p className="muted small">
        {progress.exists ? `${progress.percentage}%` : "Not started"}
        {progress.startedAt ? ` · started ${progress.startedAt}` : ""}
        {progress.completedAt ? ` · finished ${progress.completedAt}` : ""}
      </p>
      <div className="row">
        <label className="field inline">
          Current page
          <input
            className="input"
            type="number"
            min={0}
            max={pageCount ?? undefined}
            value={page}
            onChange={(e) => setPage(e.target.value)}
          />
        </label>
        <label className="field inline">
          Percent
          <input
            className="input"
            type="number"
            min={0}
            max={100}
            step="0.1"
            value={derived ? shown.toFixed(1) : percentage}
            disabled={derived}
            onChange={(e) => setPercentage(e.target.value)}
          />
        </label>
        <button className="btn secondary" disabled={save.isPending}>
          {save.isPending ? "Saving…" : "Save progress"}
        </button>
      </div>
      {pageCount ? (
        <p className="muted small">Percentage follows the page because the book has {pageCount} pages.</p>
      ) : null}
      <ErrorState error={save.error} />
    </form>
  );
}

export function ProgressForm({ bookId, pageCount }: { bookId: string; pageCount?: number | null }) {
  const progress = useProgress(bookId);
  if (progress.error) return <ErrorState error={progress.error} onRetry={() => progress.refetch()} />;
  if (!progress.data) return null;
  // Remount when the saved progress changes so the form shows the stored values.
  return <Form key={progress.dataUpdatedAt} bookId={bookId} pageCount={pageCount} progress={progress.data} />;
}
