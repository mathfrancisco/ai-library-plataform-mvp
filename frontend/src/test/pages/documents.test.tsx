import { screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/render";
import { API, server } from "@/test/server";
import { auth } from "@/lib/auth";
import Documents from "@/app/(private)/documents/page";

const failed = {
  id: "d1",
  originalName: "broken.pdf",
  contentType: "application/pdf",
  sizeBytes: 2048,
  status: "FAILED",
  failureReason: "UNSUPPORTED_FORMAT",
  errorMessage: "The file could not be parsed.",
  chunkCount: 0,
  createdAt: "2026-09-30T00:00:00Z",
};

describe("Documents page", () => {
  it("shows the failure reason with a retry and does not poll when nothing is processing", async () => {
    auth.save("t", "r");
    let calls = 0;
    server.use(
      http.get(`${API}/documents`, () => {
        calls++;
        return HttpResponse.json([failed]);
      }),
    );
    renderWithProviders(<Documents />);
    expect(await screen.findByText("The file could not be parsed.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
    await new Promise((r) => setTimeout(r, 3500));
    expect(calls).toBe(1);
  }, 10_000);

  it("rejects disallowed files on the client", async () => {
    auth.save("t", "r");
    renderWithProviders(<Documents />);
    const input = screen.getByLabelText("Upload a permitted document") as HTMLInputElement;
    const file = new File(["x"], "run.exe", { type: "application/octet-stream" });
    Object.defineProperty(input, "files", { value: [file] });
    input.dispatchEvent(new Event("change", { bubbles: true }));
    expect(await screen.findByText("Allowed formats: PDF, EPUB, TXT, Markdown.")).toBeTruthy();
  });
});
