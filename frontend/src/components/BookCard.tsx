import type { Book } from "@/types/api";
import Link from "next/link";
export function BookCard({ book, onAdd }: { book: Book; onAdd?: (b: Book) => void }) {
  const href = book.id ? `/book/${book.id}` : "#";
  return (
    <article className="bookcard">
      <Link href={href} className="cover">
        {book.coverUrl ? <img src={book.coverUrl} alt="" /> : <span>{book.title.slice(0, 1)}</span>}
      </Link>
      <div className="bookmeta">
        <p className="eyebrow">{book.categories?.[0] ?? book.provider ?? "Book"}</p>
        <h3>{book.title}</h3>
        <p>{book.authors?.join(", ") || "Unknown author"}</p>
        <div className="row">
          <span>{book.publishedYear ?? "—"}</span>
          {book.pageCount && <span>{book.pageCount} pages</span>}
        </div>
        {onAdd && (
          <button className="smallbtn" onClick={() => onAdd(book)}>
            + Want to read
          </button>
        )}
      </div>
    </article>
  );
}
