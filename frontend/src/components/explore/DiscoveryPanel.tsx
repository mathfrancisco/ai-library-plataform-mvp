"use client";
import Link from "next/link";
import { useState } from "react";
import { ErrorState } from "@/components/ErrorState";
import type { Discovery } from "@/types/api";

export function DiscoveryPanel({
  signedIn,
  pending,
  error,
  result,
  onDiscover,
}: {
  signedIn: boolean;
  pending: boolean;
  error: unknown;
  result?: Discovery;
  onDiscover: (prompt: string) => void;
}) {
  const [prompt, setPrompt] = useState("");
  return (
    <section className="panel" aria-labelledby="discover">
      <p className="eyebrow" id="discover">
        Discover with natural language
      </p>
      {signedIn ? (
        <form
          className="searchbox flush"
          onSubmit={(e) => {
            e.preventDefault();
            if (prompt.trim()) onDiscover(prompt.trim());
          }}
        >
          <input
            className="input"
            aria-label="Describe what you want to read"
            value={prompt}
            maxLength={500}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="An accessible book to learn distributed systems as a Java developer"
          />
          <button className="btn secondary" disabled={pending || !prompt.trim()}>
            {pending ? "Understanding…" : "Discover with AI"}
          </button>
        </form>
      ) : (
        <p className="muted">
          <Link href="/login?next=/explore">Sign in</Link> to use AI discovery.
        </p>
      )}
      <ErrorState error={error} />
      {result && (
        <p className="muted">
          Interpreted as: <b>{result.plan.query}</b>
          {result.plan.language ? ` · language ${result.plan.language}` : ""}
          {result.plan.categories?.length ? ` · ${result.plan.categories.join(", ")}` : ""}
        </p>
      )}
    </section>
  );
}
