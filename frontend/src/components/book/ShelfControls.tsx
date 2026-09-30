"use client";
import Link from "next/link";
import { useSignedIn } from "@/lib/auth";
import { useLibraryItem, useUpsertLibraryItem, type LibraryPatch } from "@/lib/hooks";
import { ErrorState } from "@/components/ErrorState";
import { FavoriteToggle, RatingStars, StatusSelect } from "@/components/LibraryControls";

export function ShelfControls({ bookId }: { bookId: string }) {
  const signedIn = useSignedIn();
  const item = useLibraryItem(bookId);
  const upsert = useUpsertLibraryItem();
  if (!signedIn)
    return (
      <p className="muted">
        <Link href={`/login?next=/book/${bookId}`}>Sign in</Link> to add this book to your shelf.
      </p>
    );
  const onShelf = !!item.data;
  const save = (patch: LibraryPatch) => upsert.mutate({ bookId, patch, onShelf });
  return (
    <div>
      <div className="actions">
        {onShelf ? (
          <>
            <StatusSelect
              value={item.data!.status}
              disabled={upsert.isPending}
              onChange={(status) => save({ status })}
            />
            <FavoriteToggle
              value={item.data!.favorite}
              disabled={upsert.isPending}
              onChange={(favorite) => save({ favorite })}
            />
            <RatingStars
              value={item.data!.rating}
              disabled={upsert.isPending}
              onChange={(rating) => save({ rating })}
            />
          </>
        ) : (
          <button
            className="btn"
            disabled={upsert.isPending || item.isLoading}
            onClick={() => save({ status: "WANT_TO_READ" })}
          >
            {upsert.isPending ? "Adding…" : "+ Want to read"}
          </button>
        )}
      </div>
      <ErrorState error={item.error ?? upsert.error} />
    </div>
  );
}
