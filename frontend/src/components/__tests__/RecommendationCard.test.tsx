import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { RecommendationCard } from "../RecommendationCard";
describe("RecommendationCard", () => {
  it("explains why a book is recommended", () => {
    render(
      <RecommendationCard
        item={{
          book: { id: "1", title: "Dune Messiah", authors: ["Frank Herbert"] },
          score: 0.03,
          reasons: ["More from Frank Herbert"],
        }}
      />,
    );
    expect(screen.getByText("Dune Messiah")).toBeTruthy();
    expect(screen.getByText("More from Frank Herbert")).toBeTruthy();
  });
});
