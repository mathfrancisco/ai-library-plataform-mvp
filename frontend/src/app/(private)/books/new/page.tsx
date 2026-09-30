"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { ErrorNote } from "@/components/ErrorNote";
import type { Book } from "@/types/api";
import { LIBRARY_STATUSES } from "@/types/api";
import { emptyBookForm, toCreateRequest, type BookForm } from "@/lib/books";

export default function NewBookPage() {
  const router = useRouter();
  const [f, setF] = useState<BookForm>(emptyBookForm);
  const set =
    <K extends keyof BookForm>(k: K) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
      setF((v) => ({
        ...v,
        [k]: e.target.type === "checkbox" ? (e.target as HTMLInputElement).checked : e.target.value,
      }));
  const create = useMutation({
    mutationFn: async () => {
      const b = await api<Book>("/api/books", { method: "POST", body: JSON.stringify(toCreateRequest(f)) });
      if (f.shelf)
        await api(`/api/library/books/${b.id}`, {
          method: "POST",
          body: JSON.stringify({ status: f.shelf }),
        });
      return b;
    },
    onSuccess: (b) => router.push(`/book/${b.id}`),
  });
  return (
    <main className="shell narrow">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Catalog</p>
          <h1>Add a book manually</h1>
        </div>
        <Link className="btn secondary" href="/explore">
          Search catalogs instead
        </Link>
      </div>
      <form
        className="panel formgrid"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate();
        }}
      >
        <label className="full">
          Title *<input className="input" required maxLength={500} value={f.title} onChange={set("title")} />
        </label>
        <label className="full">
          Subtitle
          <input className="input" maxLength={500} value={f.subtitle} onChange={set("subtitle")} />
        </label>
        <label>
          Authors (comma-separated)
          <input
            className="input"
            value={f.authors}
            onChange={set("authors")}
            placeholder="Robert C. Martin"
          />
        </label>
        <label>
          Categories (comma-separated)
          <input
            className="input"
            value={f.categories}
            onChange={set("categories")}
            placeholder="Software architecture"
          />
        </label>
        <label>
          ISBN-13
          <input className="input" maxLength={20} value={f.isbn13} onChange={set("isbn13")} />
        </label>
        <label>
          ISBN-10
          <input className="input" maxLength={20} value={f.isbn10} onChange={set("isbn10")} />
        </label>
        <label>
          Publisher
          <input className="input" maxLength={300} value={f.publisher} onChange={set("publisher")} />
        </label>
        <label>
          Language
          <input
            className="input"
            maxLength={16}
            value={f.language}
            onChange={set("language")}
            placeholder="en"
          />
        </label>
        <label>
          Published year
          <input
            className="input"
            type="number"
            min={0}
            max={2100}
            value={f.publishedYear}
            onChange={set("publishedYear")}
          />
        </label>
        <label>
          Page count
          <input className="input" type="number" min={1} value={f.pageCount} onChange={set("pageCount")} />
        </label>
        <label className="full">
          Cover URL
          <input
            className="input"
            type="url"
            pattern="https://.*"
            title="Must start with https://"
            value={f.coverUrl}
            onChange={set("coverUrl")}
          />
        </label>
        <label className="full">
          Description
          <textarea className="textarea" value={f.description} onChange={set("description")} />
        </label>
        <label>
          Add to my shelf as
          <select className="select" value={f.shelf} onChange={set("shelf")}>
            <option value="">Don&apos;t add</option>
            {LIBRARY_STATUSES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
        <label className="content-end">
          <span>
            <input type="checkbox" checked={f.publicDomain} onChange={set("publicDomain")} /> Public domain
          </span>
        </label>
        <div className="full">
          <ErrorNote error={create.error} />
          <button className="btn" disabled={create.isPending}>
            {create.isPending ? "Saving…" : "Add book"}
          </button>
        </div>
      </form>
    </main>
  );
}
