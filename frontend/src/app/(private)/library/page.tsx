"use client";
import { useMemo, useState } from "react";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { api, errorMessage } from "@/lib/api";
import { invalidateShelf, useLibrary, useRemoveLibraryItem, useUpsertLibraryItem } from "@/lib/hooks";
import { BookCard } from "@/components/BookCard";
import { Empty } from "@/components/Empty";
import { ErrorState } from "@/components/ErrorState";
import { CardGridSkeleton } from "@/components/Skeleton";
import { ProgressBar } from "@/components/ProgressBar";
import { useToast } from "@/components/Toast";
import { FavoriteToggle, RatingStars, StatusSelect } from "@/components/LibraryControls";
import { LIBRARY_STATUSES, type LibraryItemSummary, type LibraryStatus } from "@/types/api";

type Filter = LibraryStatus | "" | "FAVORITES";
type Sort = "added" | "title" | "rating";

const sorters: Record<Sort, (a: LibraryItemSummary, b: LibraryItemSummary) => number> = {
  added: (a, b) => b.addedAt.localeCompare(a.addedAt),
  title: (a, b) => a.book.title.localeCompare(b.book.title),
  rating: (a, b) => (b.rating ?? 0) - (a.rating ?? 0),
};

export default function LibraryPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const [filter, setFilter] = useState<Filter>("");
  const [sort, setSort] = useState<Sort>("added");
  const q = useLibrary(filter === "FAVORITES" ? "" : filter);
  const upsert = useUpsertLibraryItem();
  const remove = useRemoveLibraryItem();

  const items = useMemo(() => {
    const list = (q.data ?? []).filter((x) => filter !== "FAVORITES" || x.favorite);
    return [...list].sort(sorters[sort]);
  }, [q.data, filter, sort]);

  function removeWithUndo(item: LibraryItemSummary) {
    remove.mutate(item.book.id, {
      onSuccess: () =>
        toast({
          kind: "success",
          text: `Removed “${item.book.title}”`,
          action: {
            label: "Undo",
            run: () =>
              api(`/api/library/books/${item.book.id}`, {
                method: "POST",
                body: JSON.stringify({
                  status: item.status,
                  favorite: item.favorite,
                  rating: item.rating ?? undefined,
                }),
              })
                .then(() => invalidateShelf(qc, item.book.id))
                .catch((e) => toast({ kind: "error", text: errorMessage(e) })),
          },
        }),
      onError: (e) => toast({ kind: "error", text: errorMessage(e) }),
    });
  }

  return (
    <main className="shell">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Personal library</p>
          <h1>My shelf</h1>
        </div>
        <div className="row">
          <Link className="btn secondary" href="/books/new">
            + Add manually
          </Link>
          <select
            aria-label="Filter"
            className="select compact"
            value={filter}
            onChange={(e) => setFilter(e.target.value as Filter)}
          >
            <option value="">All books</option>
            <option value="FAVORITES">Favorites</option>
            {LIBRARY_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
          <select
            aria-label="Sort"
            className="select compact"
            value={sort}
            onChange={(e) => setSort(e.target.value as Sort)}
          >
            <option value="added">Recently added</option>
            <option value="title">Title</option>
            <option value="rating">Rating</option>
          </select>
        </div>
      </div>
      <ErrorState error={q.error} onRetry={() => q.refetch()} />
      {q.isLoading && <CardGridSkeleton />}
      {q.isSuccess && items.length === 0 && (
        <Empty
          title={filter ? "Nothing here yet" : "Your shelf is empty"}
          body={filter ? "No books match this filter." : "Search the catalog and add a book to begin."}
        />
      )}
      <div className="grid">
        {items.map((x) => {
          const id = x.book.id;
          const busy = upsert.isPending && upsert.variables?.bookId === id;
          const save = (patch: Parameters<typeof upsert.mutate>[0]["patch"]) =>
            upsert.mutate(
              { bookId: id, patch, onShelf: true },
              { onError: (e) => toast({ kind: "error", text: errorMessage(e) }) },
            );
          return (
            <BookCard key={id} book={x.book}>
              {x.status === "READING" && (
                <div className="small muted">
                  <ProgressBar value={x.percentage} label={`Progress for ${x.book.title}`} />
                  {Math.round(x.percentage)}% read
                </div>
              )}
              <div className="row">
                <StatusSelect value={x.status} disabled={busy} onChange={(status) => save({ status })} />
                <FavoriteToggle
                  value={x.favorite}
                  disabled={busy}
                  onChange={(favorite) => save({ favorite })}
                />
              </div>
              <div className="row between">
                <RatingStars value={x.rating} disabled={busy} onChange={(rating) => save({ rating })} />
                <button className="btn secondary small" onClick={() => removeWithUndo(x)}>
                  Remove
                </button>
              </div>
            </BookCard>
          );
        })}
      </div>
    </main>
  );
}
