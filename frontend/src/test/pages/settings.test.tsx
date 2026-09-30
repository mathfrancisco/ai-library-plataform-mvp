import { screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/render";
import { API, fixtures, server } from "@/test/server";
import { auth } from "@/lib/auth";
import SettingsPage from "@/app/(private)/settings/page";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

describe("Settings page", () => {
  it("updates the profile and shows AI usage by model", async () => {
    auth.save("t", "r");
    let body: unknown;
    server.use(
      http.patch(`${API}/auth/me`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...fixtures.me, displayName: "Ana Maria" });
      }),
    );
    renderWithProviders(<SettingsPage />);
    const name = await screen.findByLabelText("Display name");
    fireEvent.change(name, { target: { value: "Ana Maria" } });
    fireEvent.click(screen.getByText("Save profile"));
    await waitFor(() => expect(body).toEqual({ displayName: "Ana Maria" }));
    expect(await screen.findByText("llama-3.1-8b-instant")).toBeTruthy();
  });

  it("validates the password confirmation before sending", async () => {
    auth.save("t", "r");
    renderWithProviders(<SettingsPage />);
    fireEvent.change(screen.getByLabelText("New password"), { target: { value: "password2" } });
    fireEvent.change(screen.getByLabelText("Confirm new password"), { target: { value: "password3" } });
    expect(screen.getByText("Passwords do not match.")).toBeTruthy();
    expect((screen.getByText("Change password") as HTMLButtonElement).disabled).toBe(true);
  });

  it("requires typing DELETE before deleting the account", async () => {
    auth.save("t", "r");
    let deleted = false;
    server.use(
      http.delete(`${API}/auth/me`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<SettingsPage />);
    fireEvent.click(screen.getByText("Delete my account"));
    const confirm = screen.getByRole("button", { name: "Delete account" }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("Type DELETE to confirm"), { target: { value: "DELETE" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password1" } });
    expect(confirm.disabled).toBe(false);
    fireEvent.click(confirm);
    await waitFor(() => expect(deleted).toBe(true));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/"));
  });
});
