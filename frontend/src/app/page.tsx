"use client";
import Link from "next/link";
import { useSignedIn } from "@/lib/auth";

const steps = [
  ["Search", "Find books by title, author, ISBN or by describing what you want to read."],
  ["Shelve", "Track what you want to read, are reading and finished, with ratings and progress."],
  ["Ask", "Upload files you are allowed to use and ask questions answered from them, with sources."],
];

export default function Home() {
  const signedIn = useSignedIn();
  return (
    <main className="shell">
      <section className="hero">
        <div>
          <p className="kicker">Your library, with a reasoning layer</p>
          <h1>
            Find the book.
            <br />
            <em>Understand the idea.</em>
          </h1>
          <p className="lede">
            A modern AI-powered library for semantic discovery, personal reading management and grounded
            conversations with documents you are allowed to use.
          </p>
          <div className="actions">
            <Link className="btn" href="/explore">
              Explore books
            </Link>
            {signedIn ? (
              <Link className="btn secondary" href="/library">
                Go to my library
              </Link>
            ) : (
              <Link className="btn secondary" href="/login?mode=register">
                Create a free account
              </Link>
            )}
          </div>
        </div>
        <div className="heroPanel">
          <p className="eyebrow">Natural-language discovery</p>
          <div className="query">“I want a short psychological thriller set in a small town.”</div>
          <div className="flow">
            <div>
              <span>1 · Understand intent</span>
              <b>AI</b>
            </div>
            <div>
              <span>2 · Keyword + meaning search</span>
              <b>Hybrid</b>
            </div>
            <div>
              <span>3 · Federated catalog</span>
              <b>Open Library</b>
            </div>
            <div>
              <span>4 · Merge & dedupe</span>
              <b>RRF</b>
            </div>
          </div>
        </div>
      </section>
      <section aria-labelledby="how" className="how">
        <h2 id="how">How it works</h2>
        <ol className="steps">
          {steps.map(([title, body], i) => (
            <li key={title} className="panel">
              <span className="stepnum">{i + 1}</span>
              <h3>{title}</h3>
              <p className="muted">{body}</p>
            </li>
          ))}
        </ol>
      </section>
    </main>
  );
}
