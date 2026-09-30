import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

/** Tests run with NEXT_PUBLIC_API_URL=http://localhost (vitest.config.ts), so API calls hit this origin. */
export const API = "http://localhost/api";

export const fixtures = {
  me: {
    id: "u1",
    email: "ana@example.com",
    displayName: "Ana",
    role: "USER",
    createdAt: "2026-01-02T00:00:00Z",
  },
  book: {
    id: "b1",
    title: "Dune",
    authors: ["Frank Herbert"],
    categories: ["Science fiction"],
    pageCount: 400,
    description: "Desert planet.",
  },
  libraryItem: {
    book: { id: "b1", title: "Dune", authors: ["Frank Herbert"], categories: [], pageCount: 400 },
    status: "READING",
    favorite: false,
    rating: null,
    addedAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-02T00:00:00Z",
    percentage: 25,
  },
  usage: {
    aiEnabled: true,
    windowDays: 30,
    requests: 3,
    successful: 2,
    failed: 1,
    inputTokens: 120,
    outputTokens: 80,
    averageLatencyMs: 512.4,
    byModel: [{ key: "openai/gpt-oss-20b", requests: 3, failures: 1, inputTokens: 120, outputTokens: 80 }],
    byOperation: [],
  },
};

/** Default handlers shared by every test; tests override with server.use(...). */
export const handlers = [
  http.get(`${API}/auth/me`, () => HttpResponse.json(fixtures.me)),
  http.get(`${API}/ai/usage`, () => HttpResponse.json(fixtures.usage)),
  http.get(`${API}/library`, () => HttpResponse.json([fixtures.libraryItem])),
  http.patch(`${API}/library/books/:id`, () =>
    HttpResponse.json({ ...fixtures.libraryItem, book: fixtures.book }),
  ),
  http.get(`${API}/documents`, () => HttpResponse.json([])),
  http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 204 })),
];

export const server = setupServer(...handlers);
