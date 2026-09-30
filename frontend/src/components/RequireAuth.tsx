"use client";
import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSignedIn, useSessionKnown } from "@/lib/auth";

/** Redirects signed-out visitors to /login?next=<current path>. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const signedIn = useSignedIn();
  const known = useSessionKnown();
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    if (known && !signedIn) router.replace(`/login?next=${encodeURIComponent(pathname ?? "/")}`);
  }, [known, signedIn, router, pathname]);
  if (!signedIn) return <main className="shell" aria-busy="true" />;
  return <>{children}</>;
}
