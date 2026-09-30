import { auth } from "./auth";
import type { ApiErrorBody } from "@/types/api";
const BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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
async function refreshAccess() {
  const token = auth.refresh();
  if (!token) return false;
  // Refresh tokens rotate on use, so concurrent 401s must share one refresh call.
  refreshing ??= (async () => {
    try {
      const r = await fetch(`${BASE}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: token }),
      });
      if (!r.ok) {
        auth.clear();
        return false;
      }
      const j = await r.json();
      auth.save(j.accessToken, j.refreshToken);
      return true;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}
export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const token = auth.access();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const r = await fetch(`${BASE}${path}`, { ...init, headers, cache: "no-store" });
  if (r.status === 401 && retry && token && (await refreshAccess())) return api<T>(path, init, false);
  if (!r.ok) {
    let code = "HTTP_" + r.status,
      m = `Request failed (${r.status})`;
    try {
      const j = (await r.json()) as Partial<ApiErrorBody>;
      m = j.message ?? m;
      code = j.code ?? code;
    } catch {}
    throw new ApiError(r.status, code, m);
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
export function errorMessage(e: unknown, fallback = "Something went wrong") {
  return e instanceof Error ? e.message : fallback;
}
export { BASE };
