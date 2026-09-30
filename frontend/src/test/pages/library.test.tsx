import { screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/render";
import { API, fixtures, server } from "@/test/server";
import { auth } from "@/lib/auth";
import LibraryPage from "@/app/(private)/library/page";

describe("Library page", () => {
  it("patches status, favorite and rating, and shows progress for current reads", async () => {
    auth.save("t", "r");
    const patches: unknown[] = [];
    server.use(
      http.patch(`${API}/library/books/b1`, async ({ request }) => {
        patches.push(await request.json());
        return HttpResponse.json({ ...fixtures.libraryItem, book: fixtures.book });
      }),
    );
    renderWithProviders(<LibraryPage />);
    expect(await screen.findByText("Dune")).toBeTruthy();
    expect(screen.getByText("25% read")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("Reading status"), { target: { value: "READ" } });
    fireEvent.click(screen.getByLabelText("Mark as favorite"));
    fireEvent.click(screen.getByLabelText("Rate 4"));
    await waitFor(() => expect(patches).toEqual([{ status: "READ" }, { favorite: true }, { rating: 4 }]));
  });

  it("offers undo after removing a book", async () => {
    auth.save("t", "r");
    let restored: unknown;
    server.use(
      http.delete(`${API}/library/books/b1`, () => new HttpResponse(null, { status: 204 })),
      http.post(`${API}/library/books/b1`, async ({ request }) => {
        restored = await request.json();
        return HttpResponse.json({});
      }),
    );
    renderWithProviders(<LibraryPage />);
    fireEvent.click(await screen.findByText("Remove"));
    fireEvent.click(await screen.findByText("Undo"));
    await waitFor(() => expect(restored).toEqual({ status: "READING", favorite: false }));
  });
});
