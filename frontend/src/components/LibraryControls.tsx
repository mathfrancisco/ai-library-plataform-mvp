"use client";
import { LIBRARY_STATUSES, type LibraryStatus } from "@/types/api";

export function StatusSelect({
  value,
  onChange,
  disabled,
}: {
  value: LibraryStatus;
  onChange: (s: LibraryStatus) => void;
  disabled?: boolean;
}) {
  return (
    <select
      aria-label="Reading status"
      className="select compact"
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value as LibraryStatus)}
    >
      {LIBRARY_STATUSES.map((s) => (
        <option key={s.value} value={s.value}>
          {s.label}
        </option>
      ))}
    </select>
  );
}

export function FavoriteToggle({
  value,
  onChange,
  disabled,
}: {
  value: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      aria-pressed={value}
      aria-label={value ? "Remove from favorites" : "Mark as favorite"}
      className={`fav ${value ? "on" : ""}`}
      disabled={disabled}
      onClick={() => onChange(!value)}
    >
      {value ? "♥" : "♡"}
    </button>
  );
}

/** Clicking the current rating again clears it (sends 0). */
export function RatingStars({
  value,
  onChange,
  disabled,
}: {
  value?: number | null;
  onChange: (v: number) => void;
  disabled?: boolean;
}) {
  const current = value ?? 0;
  return (
    <span className="stars" role="group" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          type="button"
          key={n}
          aria-label={current === n ? `Clear rating` : `Rate ${n}`}
          aria-pressed={current >= n}
          className={current >= n ? "on" : ""}
          disabled={disabled}
          onClick={() => onChange(current === n ? 0 : n)}
        >
          ★
        </button>
      ))}
    </span>
  );
}
