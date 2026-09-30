import { redirect } from "next/navigation";

/** Legacy alias: /search?q=… → /explore?q=… */
export default async function SearchAlias({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[]>>;
}) {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(await searchParams)) {
    for (const value of Array.isArray(v) ? v : [v]) params.append(k, value);
  }
  const query = params.toString();
  redirect(query ? `/explore?${query}` : "/explore");
}
