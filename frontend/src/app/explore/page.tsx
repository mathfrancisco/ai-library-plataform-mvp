"use client";
import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, errorMessage } from "@/lib/api";
import { useSignedIn } from "@/lib/auth";
import { invalidateShelf } from "@/lib/hooks";
import { ErrorState } from "@/components/ErrorState";
import { CardGridSkeleton } from "@/components/Skeleton";
import { Empty } from "@/components/Empty";
import { useToast } from "@/components/Toast";
import { SearchForm } from "@/components/explore/SearchForm";
import { DiscoveryPanel } from "@/components/explore/DiscoveryPanel";
import { ResultsGrid, hitKey } from "@/components/explore/ResultsGrid";
import type { Book, Discovery, SearchHit, SearchMode, SearchResponse } from "@/types/api";

const PROVIDER_NAMES: Record<string, string> = {
  "open-library": "Open Library",
  "google-books": "Google Books",
};

function ExploreContent() {
  const params = useSearchParams();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const signedIn = useSignedIn();
  const q = params.get("q") ?? "";
  const mode = (params.get("mode") as SearchMode) || "HYBRID";
  const [discovered, setDiscovered] = useState<Discovery>();
  const [shelved, setShelved] = useState<Set<string>>(new Set());

  const search = useQuery({
    queryKey: ["search", q, mode],
    queryFn: () => api<SearchResponse>(`/api/search?q=${encodeURIComponent(q)}&mode=${mode}`),
    enabled: !!q,
  });
  const discover = useMutation({
    mutationFn: (prompt: string) =>
      api<Discovery>("/api/search/discover", { method: "POST", body: JSON.stringify({ prompt }) }),
    onSuccess: setDiscovered,
  });
  const localId = async (h: SearchHit) =>
    h.localBookId ??
    (
      await api<Book>("/api/catalog/import", {
        method: "POST",
        body: JSON.stringify({ provider: h.provider, externalId: h.externalId }),
      })
    ).id;
  const add = useMutation({
    mutationFn: async (h: SearchHit) => {
      const id = await localId(h);
      await api(`/api/library/books/${id}`, {
        method: "POST",
        body: JSON.stringify({ status: "WANT_TO_READ" }),
      });
      return id;
    },
    onSuccess: (id, h) => {
      setShelved((s) => new Set(s).add(hitKey(h)));
      invalidateShelf(qc, id);
      toast({ kind: "success", text: `“${h.title}” added to your shelf` });
    },
    onError: (e) => toast({ kind: "error", text: errorMessage(e) }),
  });
  const open = useMutation({
    mutationFn: localId,
    onSuccess: (id) => router.push(`/book/${id}`),
    onError: (e) => toast({ kind: "error", text: errorMessage(e) }),
  });

  const response: SearchResponse | undefined = discovered ?? search.data;
  const results = response?.results ?? [];
  const down = (response?.providers ?? []).filter((p) => !p.ok).map((p) => PROVIDER_NAMES[p.name] ?? p.name);
  const degraded = (response?.degraded ?? []).filter((d) => d !== "external");

  return (
    <main className="shell">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Hybrid discovery</p>
          <h1>Explore books</h1>
        </div>
        {signedIn && (
          <Link className="btn secondary" href="/books/new">
            + Add manually
          </Link>
        )}
      </div>
      <SearchForm
        key={q}
        initialQuery={q}
        initialMode={mode}
        onSearch={(nq, nm) => {
          setDiscovered(undefined);
          router.push(`/explore?q=${encodeURIComponent(nq)}&mode=${nm}`);
        }}
      />
      <DiscoveryPanel
        signedIn={signedIn}
        pending={discover.isPending}
        error={discover.error}
        result={discovered}
        onDiscover={(p) => discover.mutate(p)}
      />
      {down.length > 0 && (
        <p className="banner" role="status">
          {down.join(" and ")} {down.length > 1 ? "are" : "is"} unavailable right now; showing the other
          results.
        </p>
      )}
      {degraded.length > 0 && (
        <p className="banner" role="status">
          Some search sources were slow ({degraded.join(", ")}); results may be incomplete.
        </p>
      )}
      <ErrorState error={search.error} onRetry={() => search.refetch()} />
      {(search.isLoading || discover.isPending) && <CardGridSkeleton />}
      {!search.isLoading && !discover.isPending && response && results.length === 0 && (
        <Empty title="No books found" body="Try other words, or switch the search mode." />
      )}
      <ResultsGrid
        hits={results}
        signedIn={signedIn}
        pendingKey={add.isPending && add.variables ? hitKey(add.variables) : undefined}
        opening={open.isPending && open.variables ? hitKey(open.variables) : undefined}
        shelved={shelved}
        onAdd={(h) => add.mutate(h)}
        onOpen={(h) => open.mutate(h)}
      />
    </main>
  );
}

export default function Explore() {
  return (
    <Suspense fallback={<main className="shell" aria-busy="true" />}>
      <ExploreContent />
    </Suspense>
  );
}
