import { screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { renderWithQuery, stubFetch } from "@/test/render";
import { auth } from "@/lib/auth";
import Dashboard from "../dashboard/page";

describe("Dashboard page", () => {
  it("renders counts, current reads and recommendation reasons", async () => {
    auth.save("t", "r");
    stubFetch({
      "GET /api/dashboard": () => ({
        body: {
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
        },
      }),
      "GET /api/recommendations": () => ({
        body: [
          {
            book: { id: "b2", title: "Dune Messiah", authors: ["Frank Herbert"] },
            score: 0.1,
            reasons: ["More from Frank Herbert"],
          },
        ],
      }),
    });
    renderWithQuery(<Dashboard />);
    expect(await screen.findByText("Dune")).toBeTruthy();
    expect(screen.getByText("100 / 400 pages · 25%")).toBeTruthy();
    expect(await screen.findByText("More from Frank Herbert")).toBeTruthy();
  });
});
