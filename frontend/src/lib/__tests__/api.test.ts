import { describe, it, expect, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { api, ApiError } from "../api";
import { auth } from "../auth";
import { API, server } from "@/test/server";

describe("api client", () => {
  it("surfaces the backend error envelope code and message", async () => {
    server.use(
      http.get(`${API}/books/x`, () =>
        HttpResponse.json({ code: "BOOK_NOT_FOUND", message: "Book not found" }, { status: 404 }),
      ),
    );
    await expect(api("/api/books/x")).rejects.toMatchObject({
      name: "ApiError",
      status: 404,
      code: "BOOK_NOT_FOUND",
    });
  });

  it("ten parallel requests with an expired token cause exactly one refresh", async () => {
    auth.save("expired", "r1");
    let refreshes = 0;
    server.use(
      http.get(`${API}/library`, ({ request }) =>
        request.headers.get("Authorization") === "Bearer fresh"
          ? HttpResponse.json([])
          : HttpResponse.json({ code: "UNAUTHORIZED", message: "expired" }, { status: 401 }),
      ),
      http.post(`${API}/auth/refresh`, async () => {
        refreshes++;
        await new Promise((r) => setTimeout(r, 20));
        return HttpResponse.json({ accessToken: "fresh", refreshToken: "r2" });
      }),
    );
    const results = await Promise.all(Array.from({ length: 10 }, () => api<unknown[]>("/api/library")));
    expect(results).toHaveLength(10);
    expect(refreshes).toBe(1);
    expect(auth.refresh()).toBe("r2");
  });

  it("clears the session and redirects to login with next when refresh fails", async () => {
    auth.save("old", "bad");
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, pathname: "/library", search: "?x=1", assign });
    server.use(
      http.get(`${API}/library`, () =>
        HttpResponse.json({ code: "UNAUTHORIZED", message: "x" }, { status: 401 }),
      ),
      http.post(`${API}/auth/refresh`, () =>
        HttpResponse.json({ code: "INVALID_REFRESH_TOKEN", message: "x" }, { status: 401 }),
      ),
    );
    await expect(api("/api/library")).rejects.toBeInstanceOf(ApiError);
    expect(auth.access()).toBeNull();
    expect(assign).toHaveBeenCalledWith("/login?next=%2Flibrary%3Fx%3D1");
    vi.unstubAllGlobals();
  });
});
