import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { Nav } from "../Nav";
import { auth } from "@/lib/auth";
vi.mock("next/navigation", () => ({ usePathname: () => "/library" }));
describe("Nav", () => {
  it("toggles the mobile menu and marks the current page", () => {
    render(<Nav />);
    const button = screen.getByLabelText("Open menu");
    fireEvent.click(button);
    expect(button.getAttribute("aria-expanded")).toBe("true");
    expect(document.getElementById("main-nav")?.className).toBe("open");
    expect(screen.getByText("Library").closest("a")?.getAttribute("aria-current")).toBe("page");
    expect(screen.getByText("Sign in")).toBeTruthy();
  });
  it("shows settings when signed in", () => {
    auth.save("a", "r");
    render(<Nav />);
    expect(screen.getByText("Settings")).toBeTruthy();
  });
});
