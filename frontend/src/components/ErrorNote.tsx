import { ErrorState } from "./ErrorState";

/** Inline error without retry. */
export function ErrorNote({ error }: { error: unknown }) {
  return <ErrorState error={error} />;
}
