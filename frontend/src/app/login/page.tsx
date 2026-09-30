"use client";
import { useState } from "react";
import { api } from "@/lib/api";
import { auth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import type { AuthResponse } from "@/types/api";
export default function Login() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const router = useRouter();
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      setError("");
      const body = mode === "register" ? { displayName: name, email, password } : { email, password };
      const r = await api<AuthResponse>(`/api/auth/${mode}`, { method: "POST", body: JSON.stringify(body) });
      auth.save(r.accessToken, r.refreshToken);
      router.push("/library");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Authentication failed");
    }
  }
  return (
    <main className="shell" style={{ maxWidth: 540 }}>
      <div className="panel">
        <p className="eyebrow">Account</p>
        <h1>{mode === "login" ? "Welcome back" : "Create account"}</h1>
        <form onSubmit={submit} style={{ display: "grid", gap: 12 }}>
          {mode === "register" && (
            <input
              className="input"
              placeholder="Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          )}
          <input
            className="input"
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <input
            className="input"
            type="password"
            minLength={8}
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {error && (
            <p role="alert" className="errornote">
              {error}
            </p>
          )}
          <button className="btn">{mode === "login" ? "Sign in" : "Create account"}</button>
        </form>
        <button
          className="btn secondary"
          style={{ marginTop: 12 }}
          onClick={() => setMode(mode === "login" ? "register" : "login")}
        >
          {mode === "login" ? "Need an account?" : "Already have an account?"}
        </button>
      </div>
    </main>
  );
}
