"use client";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api, errorMessage } from "@/lib/api";
import { auth, useSignedIn } from "@/lib/auth";
import type { AuthResponse } from "@/types/api";

/** Only same-site relative paths are accepted as redirect targets. */
function safeNext(raw: string | null) {
  return raw && raw.startsWith("/") && !raw.startsWith("//") ? raw : "/library";
}

function LoginForm() {
  const params = useSearchParams();
  const router = useRouter();
  const signedIn = useSignedIn();
  const next = safeNext(params.get("next"));
  const [mode, setMode] = useState<"login" | "register">(
    params.get("mode") === "register" ? "register" : "login",
  );
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (signedIn) router.replace(next);
  }, [signedIn, next, router]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setPending(true);
    try {
      const body = mode === "register" ? { displayName: name, email, password } : { email, password };
      const r = await api<AuthResponse>(`/api/auth/${mode}`, { method: "POST", body: JSON.stringify(body) });
      auth.save(r.accessToken, r.refreshToken);
      router.push(next);
    } catch (err) {
      setError(errorMessage(err, "Authentication failed"));
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="shell narrow">
      <div className="panel">
        <p className="eyebrow">Account</p>
        <h1>{mode === "login" ? "Welcome back" : "Create account"}</h1>
        <form onSubmit={submit} className="stack">
          {mode === "register" && (
            <label className="field">
              Name
              <input
                className="input"
                autoComplete="name"
                maxLength={120}
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </label>
          )}
          <label className="field">
            Email
            <input
              className="input"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <label className="field">
            Password
            <input
              className="input"
              type="password"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              minLength={8}
              maxLength={100}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              aria-describedby={mode === "register" ? "password-hint" : undefined}
            />
          </label>
          {mode === "register" && (
            <p id="password-hint" className="muted small">
              At least 8 characters.
            </p>
          )}
          {error && (
            <p role="alert" className="errornote">
              {error}
            </p>
          )}
          <button className="btn" disabled={pending}>
            {pending ? "Please wait…" : mode === "login" ? "Sign in" : "Create account"}
          </button>
        </form>
        <button
          className="btn secondary mt-3"
          onClick={() => setMode(mode === "login" ? "register" : "login")}
        >
          {mode === "login" ? "Need an account?" : "Already have an account?"}
        </button>
      </div>
    </main>
  );
}

export default function Login() {
  return (
    <Suspense fallback={<main className="shell narrow" aria-busy="true" />}>
      <LoginForm />
    </Suspense>
  );
}
