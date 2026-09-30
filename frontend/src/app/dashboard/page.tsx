"use client";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { api } from "@/lib/api";
import { useSignedIn } from "@/lib/auth";
import { ErrorNote } from "@/components/ErrorNote";
import { Empty } from "@/components/Empty";
import { RecommendationCard } from "@/components/RecommendationCard";
import type { Dashboard as D, Recommendation } from "@/types/api";

export default function Dashboard() {
  const signedIn = useSignedIn();
  const q = useQuery({ queryKey: ["dashboard"], queryFn: () => api<D>("/api/dashboard"), enabled: signedIn });
  const rec = useQuery({
    queryKey: ["recommendations"],
    queryFn: () => api<Recommendation[]>("/api/recommendations?limit=8"),
    enabled: signedIn,
  });
  if (!signedIn)
    return (
      <main className="shell">
        <Empty
          title="Sign in to see your reading dashboard"
          body="Counts, progress and recommendations are computed from your library."
        />
        <p style={{ textAlign: "center" }}>
          <Link className="btn" href="/login">
            Sign in
          </Link>
        </p>
      </main>
    );
  const d = q.data;
  const stats: [string, string | number | undefined][] = [
    ["Total books", d?.totalBooks],
    ["Reading now", d?.reading],
    ["Finished this year", d?.completedThisYear],
    ["Pages tracked", d?.pagesTracked],
  ];
  return (
    <main className="shell">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Reading analytics</p>
          <h1>Dashboard</h1>
        </div>
      </div>
      <ErrorNote error={q.error} />
      <div className="stats">
        {stats.map(([l, v]) => (
          <div className="stat" key={l}>
            <span className="muted">{l}</span>
            <strong>{v ?? "—"}</strong>
          </div>
        ))}
      </div>
      <div className="panel" style={{ marginTop: 20 }}>
        <h2>Library state</h2>
        <p className="muted">
          Want to read: {d?.wantToRead ?? 0} · Reading: {d?.reading ?? 0} · Read: {d?.read ?? 0} · Dropped:{" "}
          {d?.dropped ?? 0} · Favorites: {d?.favorites ?? 0}
        </p>
        <p>
          Average progress on current reads: <b>{(d?.averageProgress ?? 0).toFixed(1)}%</b>
          {d?.averageRating != null && (
            <>
              {" "}
              · Average rating: <b>{d.averageRating} ★</b>
            </>
          )}
        </p>
      </div>
      {d?.currentlyReading.length ? (
        <div className="panel" style={{ marginTop: 20 }}>
          <h2>Currently reading</h2>
          <div className="doclist">
            {d.currentlyReading.map((c) => (
              <Link href={`/book/${c.bookId}`} key={c.bookId} className="docrow" style={{ display: "block" }}>
                <div className="row" style={{ justifyContent: "space-between" }}>
                  <b>{c.title}</b>
                  <span className="muted">
                    {c.currentPage}
                    {c.pageCount ? ` / ${c.pageCount}` : ""} pages · {c.percentage.toFixed(0)}%
                  </span>
                </div>
                <div className="progress">
                  <span style={{ width: `${c.percentage}%` }} />
                </div>
              </Link>
            ))}
          </div>
        </div>
      ) : null}
      <ErrorNote error={rec.error} />
      {rec.data?.length ? (
        <section style={{ marginTop: 32 }}>
          <div className="sectionhead">
            <h2>Recommended for you</h2>
          </div>
          <div className="grid">
            {rec.data.map((r) => (
              <RecommendationCard key={r.book.id} item={r} />
            ))}
          </div>
        </section>
      ) : null}
    </main>
  );
}
