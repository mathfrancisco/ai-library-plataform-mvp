import { errorMessage } from "@/lib/api";

/** Error block with an optional retry. Renders nothing without an error. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null;
  return (
    <div role="alert" className="errornote row justify-between">
      <span>{errorMessage(error)}</span>
      {onRetry && (
        <button className="btn secondary small" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
