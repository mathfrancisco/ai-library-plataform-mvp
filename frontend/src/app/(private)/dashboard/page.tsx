"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAiUsage } from "@/lib/hooks";
import { ErrorState } from "@/components/ErrorState";
import { ProgressBar } from "@/components/ProgressBar";
import { RecommendationCard } from "@/components/RecommendationCard";
import { Skeleton } from "@/components/Skeleton";
import { LIBRARY_STATUSES, type Dashboard as D, type Recommendation } from "@/types/api";

const STATUS_CLASS = { WANT_TO_READ: "want", READING: "reading", READ: "read", DROPPED: "dropped" } as const;

function StatusBreakdown({ d }: { d: D }) {
  const counts = { WANT_TO_READ: d.wantToRead, READING: d.reading, READ: d.read, DROPPED: d.dropped };
  const total = Math.max(1, d.totalBooks);
  return (
    <div>
      <div
        className="stackbar"
        role="img"
        aria-label={LIBRARY_STATUSES.map((s) => `${s.label}: ${counts[s.value]}`).join(", ")}
      >
        {LIBRARY_STATUSES.map((s) =>
          counts[s.value] ? (
            <span
              key={s.value}
              className={STATUS_CLASS[s.value]}
              style={{ width: `${(counts[s.value] / total) * 100}%` }}
            />
          ) : null,
        )}
      </div>
      <ul className="legend">
        {LIBRARY_STATUSES.map((s) => (
          <li key={s.value}>
            <i className={STATUS_CLASS[s.value]} />
            {s.label} <b>{counts[s.value]}</b>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function Dashboard() {
  const q = useQuery({ queryKey: ["dashboard"], queryFn: () => api<D>("/api/dashboard") });
  const rec = useQuery({
    queryKey: ["recommendations"],
    queryFn: () => api<Recommendation[]>("/api/recommendations?limit=8"),
  });
  const usage = useAiUsage();
  const d = q.data;
  const stats: [string, string | number | undefined][] = [
    ["Total books", d?.totalBooks],
    ["Reading now", d?.reading],
    ["Finished this year", d?.completedThisYear],
    ["Pages tracked", d?.pagesTracked],
  ];
  return (
    <main className="shell stack">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Reading analytics</p>
          <h1>Dashboard</h1>
        </div>
      </div>
      <ErrorState error={q.error} onRetry={() => q.refetch()} />
      <div className="stats">
        {stats.map(([l, v]) => (
          <div className="stat" key={l}>
            <span className="muted">{l}</span>
            <strong>{q.isLoading ? <Skeleton height={34} width={60} /> : (v ?? "—")}</strong>
          </div>
        ))}
      </div>
      {d && (
        <div className="twocol">
          <section className="panel">
            <h2>Library state</h2>
            <StatusBreakdown d={d} />
            <p>
              Average progress on current reads: <b>{d.averageProgress.toFixed(1)}%</b>
            </p>
            <ProgressBar value={d.averageProgress} label="Average progress" />
            <p className="muted">
              Favorites: {d.favorites}
              {d.averageRating != null && <> · Average rating: {d.averageRating} ★</>}
            </p>
          </section>
          <section className="panel">
            <h2>AI usage</h2>
            {usage.data ? (
              usage.data.aiEnabled ? (
                <p className="muted">
                  {usage.data.requests} requests in the last {usage.data.windowDays} days ·{" "}
                  {usage.data.failed} failed · {usage.data.inputTokens + usage.data.outputTokens} tokens
                </p>
              ) : (
                <p className="muted">AI features are turned off on this server.</p>
              )
            ) : (
              <Skeleton />
            )}
            <Link href="/settings">Details</Link>
          </section>
        </div>
      )}
      {d?.currentlyReading.length ? (
        <section className="panel">
          <h2>Currently reading</h2>
          <ul className="doclist">
            {d.currentlyReading.map((c) => (
              <li key={c.bookId}>
                <Link href={`/book/${c.bookId}`} className="docrow block">
                  <div className="row between">
                    <b>{c.title}</b>
                    <span className="muted">
                      {c.currentPage}
                      {c.pageCount ? ` / ${c.pageCount}` : ""} pages · {c.percentage.toFixed(0)}%
                    </span>
                  </div>
                  <ProgressBar value={c.percentage} label={`Progress for ${c.title}`} />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {d?.recentActivity.length ? (
        <section className="panel">
          <h2>Recent activity</h2>
          <ul className="activity">
            {d.recentActivity.map((a) => (
              <li key={a.bookId}>
                <Link href={`/book/${a.bookId}`}>{a.title}</Link>
                <span className="muted">
                  {LIBRARY_STATUSES.find((s) => s.value === a.status)?.label} · {Math.round(a.percentage)}% ·{" "}
                  {new Date(a.updatedAt).toLocaleDateString()}
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      <section>
        <div className="sectionhead">
          <h2>Recommended for you</h2>
        </div>
        <ErrorState error={rec.error} onRetry={() => rec.refetch()} />
        {rec.data?.length ? (
          <div className="grid">
            {rec.data.map((r) => (
              <RecommendationCard key={r.book.id} item={r} />
            ))}
          </div>
        ) : rec.isSuccess ? (
          <p className="muted">Add and rate books to get recommendations.</p>
        ) : null}
      </section>
    </main>
  );
}
