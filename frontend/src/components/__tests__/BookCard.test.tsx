import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { BookCard } from "../BookCard";

describe("BookCard", () => {
  it("links local books to their page", () => {
    render(<BookCard book={{ id: "1", title: "Clean Architecture", authors: ["Robert C. Martin"] }} />);
    expect(screen.getByRole("link", { name: "Clean Architecture" }).getAttribute("href")).toBe("/book/1");
  });

  it("renders external books without a dead link", () => {
    render(<BookCard book={{ title: "External", authors: ["Someone"] }} />);
    expect(screen.queryAllByRole("link")).toHaveLength(0);
    expect(screen.getByText("External")).toBeTruthy();
  });
});
