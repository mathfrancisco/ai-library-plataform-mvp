"use client";
import Link from "next/link";

export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="shell narrow">
      <div className="panel" role="alert">
        <p className="eyebrow">Something went wrong</p>
        <h1>We could not show this page</h1>
        <p className="muted">{error.digest ? `Reference: ${error.digest}` : "Try again in a moment."}</p>
        <div className="row">
          <button className="btn" onClick={reset}>
            Try again
          </button>
          <Link className="btn secondary" href="/">
            Go home
          </Link>
        </div>
      </div>
    </main>
  );
}
