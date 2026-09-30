import { describe, it, expect } from "vitest";
import { emptyBookForm, toCreateRequest } from "../books";

describe("toCreateRequest", () => {
  it("maps comma lists to arrays, strips ISBN formatting and drops blanks", () => {
    const r = toCreateRequest({
      ...emptyBookForm,
      title: "  Dune ",
      authors: "Frank Herbert, , Brian Herbert",
      categories: "Sci-fi",
      pageCount: "412",
      isbn13: "978-0-441-01359-3",
      publishedYear: "",
    });
    expect(r).toMatchObject({
      title: "Dune",
      authors: ["Frank Herbert", "Brian Herbert"],
      categories: ["Sci-fi"],
      isbn13: "9780441013593",
      pageCount: 412,
      publicDomain: false,
    });
    expect(r.publishedYear).toBeUndefined();
    expect(r.isbn10).toBeUndefined();
  });
});
