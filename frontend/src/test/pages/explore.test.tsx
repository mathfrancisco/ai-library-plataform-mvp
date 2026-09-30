import { screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/render";
import { API, server } from "@/test/server";
import { auth } from "@/lib/auth";
import Explore from "@/app/explore/page";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("q=dune&mode=HYBRID"),
}));

const results = {
  results: [
    {
      externalId: "OL1W",
      provider: "open-library",
      title: "Dune",
      authors: ["Frank Herbert"],
      score: 0.03,
      matchType: "EXTERNAL",
    },
    {
      externalId: "OL2W",
      provider: "open-library",
      title: "Dune Messiah",
      authors: ["Frank Herbert"],
      score: 0.02,
      matchType: "EXTERNAL",
    },
  ],
  degraded: [],
  providers: [
    { name: "open-library", ok: true },
    { name: "google-books", ok: false },
  ],
};

describe("Explore page", () => {
  it("keeps the pending state per card and reports an unavailable provider", async () => {
    auth.save("t", "r");
    let release!: () => void;
    server.use(
      http.get(`${API}/search`, () => HttpResponse.json(results)),
      http.post(`${API}/catalog/import`, async () => {
        await new Promise<void>((r) => (release = r));
        return HttpResponse.json({ id: "b1", title: "Dune", authors: [] });
      }),
      http.post(`${API}/library/books/b1`, () => HttpResponse.json({})),
    );
    renderWithProviders(<Explore />);
    expect(
      await screen.findByText("Google Books is unavailable right now; showing the other results."),
    ).toBeTruthy();
    const buttons = await screen.findAllByText("+ Want to read");
    fireEvent.click(buttons[0]);
    expect(await screen.findByText("Adding…")).toBeTruthy();
    expect(screen.getAllByText("+ Want to read")).toHaveLength(1);
    release();
    expect(await screen.findByText("In your shelf")).toBeTruthy();
  });

  it("opens an external result by importing it", async () => {
    auth.save("t", "r");
    server.use(
      http.get(`${API}/search`, () => HttpResponse.json(results)),
      http.post(`${API}/catalog/import`, () => HttpResponse.json({ id: "b9", title: "Dune", authors: [] })),
    );
    renderWithProviders(<Explore />);
    fireEvent.click((await screen.findAllByText("Details"))[0]);
    await waitFor(() => expect(push).toHaveBeenCalledWith("/book/b9"));
  });
});
