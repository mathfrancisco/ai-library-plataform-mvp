import { screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/render";
import { API, server } from "@/test/server";
import { auth } from "@/lib/auth";
import Dashboard from "@/app/(private)/dashboard/page";

describe("Dashboard page", () => {
  it("renders counts, current reads, recent activity and recommendation reasons", async () => {
    auth.save("t", "r");
    server.use(
      http.get(`${API}/dashboard`, () =>
        HttpResponse.json({
          totalBooks: 3,
          wantToRead: 1,
          reading: 1,
          read: 1,
          dropped: 0,
          favorites: 1,
          pagesTracked: 100,
          averageProgress: 25,
          averageRating: 4.5,
          completedThisYear: 1,
          currentlyReading: [
            { bookId: "b1", title: "Dune", currentPage: 100, pageCount: 400, percentage: 25 },
          ],
          recentActivity: [
            {
              bookId: "b2",
              title: "Foundation",
              status: "READ",
              percentage: 100,
              updatedAt: "2026-09-30T00:00:00Z",
            },
          ],
        }),
      ),
      http.get(`${API}/recommendations`, () =>
        HttpResponse.json([
          {
            book: { id: "b3", title: "Dune Messiah", authors: ["Frank Herbert"] },
            score: 0.1,
            reasons: ["More from Frank Herbert"],
          },
        ]),
      ),
    );
    renderWithProviders(<Dashboard />);
    expect(await screen.findByText("100 / 400 pages · 25%")).toBeTruthy();
    expect(screen.getByText("Foundation")).toBeTruthy();
    expect(await screen.findByText("More from Frank Herbert")).toBeTruthy();
    expect(screen.getByText(/3 requests in the last 30 days/)).toBeTruthy();
  });

  it("explains how to get recommendations when there are none", async () => {
    auth.save("t", "r");
    server.use(
      http.get(`${API}/dashboard`, () =>
        HttpResponse.json({
          totalBooks: 0,
          wantToRead: 0,
          reading: 0,
          read: 0,
          dropped: 0,
          favorites: 0,
          pagesTracked: 0,
          averageProgress: 0,
          completedThisYear: 0,
          currentlyReading: [],
          recentActivity: [],
        }),
      ),
      http.get(`${API}/recommendations`, () => HttpResponse.json([])),
    );
    renderWithProviders(<Dashboard />);
    expect(await screen.findByText("Add and rate books to get recommendations.")).toBeTruthy();
  });
});
