import { auth } from "./auth";
import type { ApiErrorBody } from "@/types/api";

/**
 * Same-origin by default: the browser calls /api/* on the frontend, and Next rewrites it to the backend
 * (API_INTERNAL_URL, see next.config.ts). NEXT_PUBLIC_API_URL is only for running the UI against a remote API.
 */
const BASE = process.env.NEXT_PUBLIC_API_URL ?? "";

/** Error carrying the backend error envelope code (e.g. BOOK_NOT_FOUND, AI_DISABLED). */
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

let refreshing: Promise<boolean> | null = null;

/** Refresh tokens rotate on use, so concurrent 401s must share one refresh call. */
function refreshAccess(): Promise<boolean> {
  const token = auth.refresh();
  if (!token) return Promise.resolve(false);
  refreshing ??= (async () => {
    try {
      const r = await fetch(`${BASE}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: token }),
      });
      if (!r.ok) return false;
      const j = await r.json();
      auth.save(j.accessToken, j.refreshToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

/** Called when the session cannot be refreshed: clear tokens and go to /login?next=<current path>. */
function sessionExpired() {
  auth.clear();
  if (typeof window === "undefined" || window.location.pathname.startsWith("/login")) return;
  const next = encodeURIComponent(window.location.pathname + window.location.search);
  // Outside React (no router here); a full load also drops any in-memory state of the expired session.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(`/login?next=${next}`);
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const token = auth.access();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const r = await fetch(`${BASE}${path}`, { ...init, headers, cache: "no-store" });
  if (r.status === 401 && token && retry) {
    if (await refreshAccess()) return api<T>(path, init, false);
    sessionExpired();
  }
  if (!r.ok) {
    let code = `HTTP_${r.status}`;
    let message = `Request failed (${r.status})`;
    try {
      const j = (await r.json()) as Partial<ApiErrorBody>;
      message = j.message ?? message;
      code = j.code ?? code;
    } catch {}
    throw new ApiError(r.status, code, message);
  }
  if (r.status === 204) return undefined as T;
  return r.json();
}

export async function logout() {
  const token = auth.refresh();
  try {
    if (token)
      await api("/api/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken: token }) }, false);
  } catch {
  } finally {
    auth.clear();
  }
}

/** Friendlier text for codes the UI can explain better than the raw message. */
export function errorMessage(e: unknown, fallback = "Something went wrong") {
  if (e instanceof ApiError) {
    switch (e.code) {
      case "AI_DISABLED":
        return "AI features are turned off on this server.";
      case "AI_RATE_LIMITED":
      case "RATE_LIMITED":
        return "Too many requests right now. Wait a moment and try again.";
      case "AI_TIMEOUT":
        return "The AI provider took too long to answer. Try again.";
      case "FILE_TOO_LARGE":
        return "That file is larger than 25 MB.";
      default:
        return e.message;
    }
  }
  return e instanceof Error ? e.message : fallback;
}

export function isNotFound(e: unknown) {
  return e instanceof ApiError && e.status === 404;
}

export { BASE };
