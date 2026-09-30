"use client";
import { useQuery } from "@tanstack/react-query";
import { api, isNotFound } from "@/lib/api";
import { useSignedIn } from "@/lib/auth";
import { useBook } from "@/lib/hooks";
import { BookCard } from "@/components/BookCard";
import { ErrorState } from "@/components/ErrorState";
import { Skeleton } from "@/components/Skeleton";
import { Empty } from "@/components/Empty";
import { ShelfControls } from "@/components/book/ShelfControls";
import { SummaryTabs } from "@/components/book/SummaryTabs";
import { ProgressForm } from "@/components/book/ProgressForm";
import { BookDocuments } from "@/components/book/BookDocuments";
import type { Book } from "@/types/api";

export function BookDetail({ id }: { id: string }) {
  const signedIn = useSignedIn();
  const q = useBook(id);
  const similar = useQuery({
    queryKey: ["similar", id],
    queryFn: () => api<Book[]>(`/api/books/${id}/similar?limit=4`),
    enabled: !!q.data,
  });

  if (q.isLoading)
    return (
      <main className="shell" aria-busy="true">
        <div className="bookdetail">
          <Skeleton height={360} />
          <div>
            <Skeleton height={48} width="70%" />
            <Skeleton />
            <Skeleton />
          </div>
        </div>
      </main>
    );
  if (isNotFound(q.error))
    return (
      <main className="shell">
        <Empty title="Book not found" body="It may have been removed, or the link is wrong." />
      </main>
    );
  if (q.error || !q.data)
    return (
      <main className="shell">
        <ErrorState error={q.error} onRetry={() => q.refetch()} />
      </main>
    );

  const b = q.data;
  return (
    <main className="shell stack">
      <div className="bookdetail">
        <div className="cover large">
          {b.coverUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={b.coverUrl} alt={`Cover of ${b.title}`} />
          ) : (
            <span aria-hidden="true">{b.title[0]}</span>
          )}
        </div>
        <div>
          <p className="eyebrow">{b.categories?.join(" · ") || "Book"}</p>
          <h1 className="booktitle">{b.title}</h1>
          {b.subtitle && <p className="lede">{b.subtitle}</p>}
          <p className="lede">
            {b.authors.join(", ") || "Unknown author"} · {b.publishedYear ?? "Unknown year"} ·{" "}
            {b.pageCount ?? "?"} pages
          </p>
          <p className="muted prewrap">{b.description ?? "No catalog description available."}</p>
          <ShelfControls bookId={id} />
        </div>
      </div>
      <SummaryTabs bookId={id} hasDescription={!!b.description} />
      {signedIn && <ProgressForm bookId={id} pageCount={b.pageCount} />}
      {signedIn && <BookDocuments bookId={id} />}
      {similar.data?.length ? (
        <section>
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
