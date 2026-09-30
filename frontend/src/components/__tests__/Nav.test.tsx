import { screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { Nav } from "../Nav";
import { auth } from "@/lib/auth";
import { renderWithProviders } from "@/test/render";

vi.mock("next/navigation", () => ({ usePathname: () => "/library", useRouter: () => ({ push: vi.fn() }) }));

describe("Nav", () => {
  it("opens the mobile menu with every link and marks the current page", () => {
    renderWithProviders(<Nav />);
    const button = screen.getByLabelText("Open menu");
    fireEvent.click(button);
    expect(button.getAttribute("aria-expanded")).toBe("true");
    const nav = document.getElementById("main-nav")!;
    expect(nav.className).toBe("open");
    expect([...nav.querySelectorAll("a")].map((a) => a.textContent)).toEqual([
      "Explore",
      "Library",
      "Documents",
      "Assistant",
      "Dashboard",
    ]);
    expect(screen.getByText("Library").closest("a")?.getAttribute("aria-current")).toBe("page");
    expect(screen.getByText("Sign in")).toBeTruthy();
  });

  it("shows the user's name with an account menu when signed in", async () => {
    auth.save("a", "r");
    renderWithProviders(<Nav />);
    const account = await screen.findByRole("button", { name: /Ana/ });
    fireEvent.click(account);
    expect(screen.getByRole("menuitem", { name: "Settings" })).toBeTruthy();
    expect(screen.getByRole("menuitem", { name: "Sign out" })).toBeTruthy();
  });
});
