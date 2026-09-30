import Link from "next/link";

export default function NotFound() {
  return (
    <main className="shell narrow">
      <div className="panel">
        <p className="eyebrow">404</p>
        <h1>Page not found</h1>
        <p className="muted">The page you are looking for does not exist or was moved.</p>
        <Link className="btn" href="/">
          Go home
        </Link>
      </div>
    </main>
  );
}
