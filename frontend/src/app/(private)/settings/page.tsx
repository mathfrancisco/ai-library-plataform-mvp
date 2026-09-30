"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, logout } from "@/lib/api";
import { auth } from "@/lib/auth";
import { useAiUsage, useMe } from "@/lib/hooks";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { ErrorState } from "@/components/ErrorState";
import { Skeleton } from "@/components/Skeleton";
import { useToast } from "@/components/Toast";
import type { AuthResponse, User, UsageBreakdown } from "@/types/api";

function ProfileForm({ user }: { user: User }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [name, setName] = useState(user.displayName);
  const save = useMutation({
    mutationFn: () =>
      api<User>("/api/auth/me", { method: "PATCH", body: JSON.stringify({ displayName: name.trim() }) }),
    onSuccess: (u) => {
      qc.setQueryData(["me"], u);
      toast({ kind: "success", text: "Profile updated" });
    },
  });
  return (
    <form
      className="stack"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <label className="field">
        Email
        <input className="input" value={user.email} readOnly />
      </label>
      <label className="field">
        Display name
        <input
          className="input"
          required
          maxLength={120}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <p className="muted small">
        Member since {new Date(user.createdAt).toLocaleDateString()} · role {user.role}
      </p>
      <ErrorState error={save.error} />
      <button className="btn" disabled={save.isPending || !name.trim() || name.trim() === user.displayName}>
        {save.isPending ? "Saving…" : "Save profile"}
      </button>
    </form>
  );
}

function PasswordForm() {
  const toast = useToast();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const mismatch = confirm.length > 0 && next !== confirm;
  const change = useMutation({
    mutationFn: () =>
      api<AuthResponse>("/api/auth/me/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword: current, newPassword: next }),
      }),
    onSuccess: (r) => {
      auth.save(r.accessToken, r.refreshToken);
      setCurrent("");
      setNext("");
      setConfirm("");
      toast({ kind: "success", text: "Password changed. Other devices were signed out." });
    },
  });
  return (
    <form
      className="stack"
      onSubmit={(e) => {
        e.preventDefault();
        if (!mismatch) change.mutate();
      }}
    >
      <label className="field">
        Current password
        <input
          className="input"
          type="password"
          autoComplete="current-password"
          required
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
      </label>
      <label className="field">
        New password
        <input
          className="input"
          type="password"
          autoComplete="new-password"
          minLength={8}
          maxLength={100}
          required
          value={next}
          onChange={(e) => setNext(e.target.value)}
        />
      </label>
      <label className="field">
        Confirm new password
        <input
          className="input"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          aria-invalid={mismatch}
        />
      </label>
      <p className="muted small">At least 8 characters.</p>
      {mismatch && (
        <p role="alert" className="errornote">
          Passwords do not match.
        </p>
      )}
      <ErrorState error={change.error} />
      <button className="btn" disabled={change.isPending || mismatch || next.length < 8}>
        {change.isPending ? "Changing…" : "Change password"}
      </button>
    </form>
  );
}

function Breakdown({ title, rows }: { title: string; rows: UsageBreakdown[] }) {
  if (!rows.length) return null;
  return (
    <div className="tablewrap">
      <table className="table">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th>{title}</th>
            <th>Requests</th>
            <th>Failures</th>
            <th>Tokens in / out</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.key}>
              <td>{r.key}</td>
              <td>{r.requests}</td>
              <td>{r.failures}</td>
              <td>
                {r.inputTokens} / {r.outputTokens}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function SettingsPage() {
  const router = useRouter();
  const toast = useToast();
  const me = useMe();
  const usage = useAiUsage();
  const [deleting, setDeleting] = useState(false);
  const [password, setPassword] = useState("");
  const logoutAll = useMutation({
    mutationFn: () => api("/api/auth/logout-all", { method: "POST" }),
    onSuccess: async () => {
      await logout();
      router.push("/login");
    },
    onError: (e) => toast({ kind: "error", text: String(e) }),
  });
  const remove = useMutation({
    mutationFn: () => api("/api/auth/me", { method: "DELETE", body: JSON.stringify({ password }) }),
    onSuccess: () => {
      auth.clear();
      router.push("/");
    },
  });
  async function signOut() {
    await logout();
    router.push("/login");
  }
  const u = usage.data;
  return (
    <main className="shell narrow stack">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Account</p>
          <h1>Settings</h1>
        </div>
        <button className="btn secondary" onClick={signOut}>
          Sign out
        </button>
      </div>
      <section className="panel">
        <h2>Profile</h2>
        <ErrorState error={me.error} onRetry={() => me.refetch()} />
        {me.data ? <ProfileForm key={me.data.displayName} user={me.data} /> : <Skeleton height={120} />}
      </section>
      <section className="panel">
        <h2>Security</h2>
        <PasswordForm />
        <hr />
        <p className="muted">Lost a device? Sign out everywhere, including this browser.</p>
        <button className="btn secondary" disabled={logoutAll.isPending} onClick={() => logoutAll.mutate()}>
          Sign out of all devices
        </button>
      </section>
      <section className="panel">
        <h2>AI usage</h2>
        <ErrorState error={usage.error} onRetry={() => usage.refetch()} />
        {u && !u.aiEnabled && <p className="muted">AI features are turned off on this server.</p>}
        {u && (
          <>
            <div className="stats">
              <div className="stat">
                <span className="muted">Requests ({u.windowDays} days)</span>
                <strong>{u.requests}</strong>
              </div>
              <div className="stat">
                <span className="muted">Failed</span>
                <strong>{u.failed}</strong>
              </div>
              <div className="stat">
                <span className="muted">Tokens in / out</span>
                <strong>
                  {u.inputTokens} / {u.outputTokens}
                </strong>
              </div>
              <div className="stat">
                <span className="muted">Avg latency</span>
                <strong>{Math.round(u.averageLatencyMs)} ms</strong>
              </div>
            </div>
            <Breakdown title="Model" rows={u.byModel} />
            <Breakdown title="Operation" rows={u.byOperation} />
          </>
        )}
      </section>
      <section className="panel danger">
        <h2>Delete account</h2>
        <p className="muted">
          Permanently removes your shelf, reading progress, uploaded documents and their indexed chunks, and
          AI history.
        </p>
        <button className="btn danger" onClick={() => setDeleting(true)}>
          Delete my account
        </button>
      </section>
      <ConfirmDialog
        open={deleting}
        title="Delete your account?"
        body="This cannot be undone."
        confirmLabel="Delete account"
        requireText="DELETE"
        busy={remove.isPending}
        onCancel={() => {
          setDeleting(false);
          setPassword("");
        }}
        onConfirm={() => remove.mutate()}
      >
        <label className="field">
          Password
          <input
            className="input"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <ErrorState error={remove.error} />
      </ConfirmDialog>
    </main>
  );
}
