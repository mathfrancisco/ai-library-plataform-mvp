import type { ReactNode } from "react";
import { RequireAuth } from "@/components/RequireAuth";

/** Every route in this group needs a session; signed-out visitors go to /login?next=… */
export default function PrivateLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
