import type { Recommendation } from "@/types/api";
import { BookCard } from "./BookCard";

export function RecommendationCard({ item }: { item: Recommendation }) {
  return (
    <BookCard book={item.book}>
      {item.reasons.length > 0 && (
        <ul className="reasons">
          {item.reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      )}
    </BookCard>
  );
}
