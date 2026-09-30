import type { ChatTurn } from "@/types/api";

export type Msg = { role: "user" | "assistant" | "error"; text: string };
export const MAX_HISTORY = 10;

/** Last completed user/assistant turns, oldest first, excluding the greeting (index 0) and error bubbles. */
export function historyFrom(msgs: Msg[]): ChatTurn[] {
  return msgs
    .slice(1)
    .filter((m) => m.role !== "error")
    .map((m) => ({ role: m.role === "user" ? ("USER" as const) : ("ASSISTANT" as const), text: m.text }))
    .slice(-MAX_HISTORY);
}
