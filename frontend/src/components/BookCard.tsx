import Link from "next/link";
import type { ReactNode } from "react";

type CardBook = {
  id?: string | null;
  title: string;
  authors?: string[];
  categories?: string[];
  coverUrl?: string | null;
  publishedYear?: number | null;
  pageCount?: number | null;
};

function Cover({ book }: { book: CardBook }) {
  return book.coverUrl ? (
    // Covers come from several catalog hosts; plain lazy <img> avoids an image-optimizer allow-list.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={book.coverUrl} alt={`Cover of ${book.title}`} loading="lazy" />
  ) : (
    <span aria-hidden="true">{book.title.slice(0, 1)}</span>
  );
}

/** Local books link to their detail page; external ones render as a plain card (no dead "#" link). */
export function BookCard({
  book,
  eyebrow,
  children,
}: {
  book: CardBook;
  eyebrow?: string;
  children?: ReactNode;
}) {
  return (
    <article className="bookcard">
      {book.id ? (
        <Link href={`/book/${book.id}`} className="cover" aria-label={`Open ${book.title}`}>
          <Cover book={book} />
        </Link>
      ) : (
        <div className="cover">
          <Cover book={book} />
        </div>
      )}
      <div className="bookmeta">
        <p className="eyebrow">{eyebrow ?? book.categories?.[0] ?? "Book"}</p>
        <h3>{book.id ? <Link href={`/book/${book.id}`}>{book.title}</Link> : book.title}</h3>
        <p>{book.authors?.join(", ") || "Unknown author"}</p>
        <div className="row meta">
          <span>{book.publishedYear ?? "—"}</span>
          {book.pageCount ? <span>{book.pageCount} pages</span> : null}
        </div>
        {children}
      </div>
    </article>
  );
}
