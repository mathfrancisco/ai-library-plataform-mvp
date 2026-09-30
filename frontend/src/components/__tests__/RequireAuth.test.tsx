import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { RequireAuth } from "../RequireAuth";
import { auth } from "@/lib/auth";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ usePathname: () => "/documents", useRouter: () => ({ replace }) }));

describe("RequireAuth", () => {
  it("redirects signed-out visitors to login with the next param", () => {
    render(
      <RequireAuth>
        <p>secret</p>
      </RequireAuth>,
    );
    expect(replace).toHaveBeenCalledWith("/login?next=%2Fdocuments");
    expect(screen.queryByText("secret")).toBeNull();
  });

  it("renders children with a session", () => {
    auth.save("a", "r");
    render(
      <RequireAuth>
        <p>secret</p>
      </RequireAuth>,
    );
    expect(screen.getByText("secret")).toBeTruthy();
  });
});
