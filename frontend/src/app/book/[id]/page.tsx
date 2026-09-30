import type { Metadata } from "next";
import { BookDetail } from "./BookDetail";

type Props = { params: Promise<{ id: string }> };

/** Server-side title lookup for the tab/preview; the page itself renders client-side. */
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const base = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
  try {
    const r = await fetch(`${base}/api/books/${id}`, { next: { revalidate: 300 } });
    if (!r.ok) return { title: "Book" };
    const book = (await r.json()) as { title: string; authors?: string[]; description?: string };
    return {
      title: book.title,
      description: book.description?.slice(0, 160) ?? `${book.title} by ${book.authors?.join(", ")}`,
    };
  } catch {
    return { title: "Book" };
  }
}

export default async function BookPage({ params }: Props) {
  const { id } = await params;
  return <BookDetail id={id} />;
}
