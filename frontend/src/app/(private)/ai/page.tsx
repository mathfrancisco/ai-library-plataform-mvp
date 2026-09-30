"use client";
import { useEffect, useRef, useState } from "react";
import { api, errorMessage } from "@/lib/api";
import { historyFrom, type Msg } from "@/lib/chat";

const STORAGE_KEY = "ailib.assistant";
const GREETING: Msg = {
  role: "assistant",
  text: "Ask about your library or reading progress, or ask me to add a book to your shelf.",
};
const EXAMPLES = [
  "What am I currently reading?",
  "Which books have I finished?",
  "Add Dune to my want-to-read list",
];

function load(): Msg[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Msg[]) : [GREETING];
  } catch {
    return [GREETING];
  }
}

export default function AiPage() {
  const [msgs, setMsgs] = useState<Msg[]>([GREETING]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const end = useRef<HTMLDivElement>(null);

  // sessionStorage is only readable after mount.
  useEffect(() => setMsgs(load()), []);
  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(msgs));
    } catch {}
    end.current?.scrollIntoView?.({ block: "end" });
  }, [msgs]);

  async function send(message: string, base = msgs) {
    if (!message.trim() || busy) return;
    const history = historyFrom(base);
    setMsgs([...base, { role: "user", text: message }]);
    setText("");
    setBusy(true);
    try {
      const r = await api<{ answer: string }>("/api/ai/assistant", {
        method: "POST",
        body: JSON.stringify({ message, history }),
      });
      setMsgs((v) => [...v, { role: "assistant", text: r.answer }]);
    } catch (e) {
      setMsgs((v) => [...v, { role: "error", text: errorMessage(e, "The assistant could not answer") }]);
    } finally {
      setBusy(false);
    }
  }

  function retry() {
    const lastUser = [...msgs].reverse().findIndex((m) => m.role === "user");
    if (lastUser < 0) return;
    const index = msgs.length - 1 - lastUser;
    send(msgs[index].text, msgs.slice(0, index));
  }

  return (
    <main className="shell narrow">
      <div className="sectionhead">
        <div>
          <p className="eyebrow">Spring AI tool calling</p>
          <h1>Library assistant</h1>
        </div>
        <button className="btn secondary" onClick={() => setMsgs([GREETING])} disabled={busy}>
          New chat
        </button>
      </div>
      <div className="panel chat" aria-live="polite" aria-busy={busy}>
        {msgs.map((m, i) => (
          <div className={`bubble ${m.role}`} key={i}>
            <span className="prewrap">{m.text}</span>
            {m.role === "error" && i === msgs.length - 1 && (
              <button className="linkbtn" onClick={retry}>
                Retry
              </button>
            )}
          </div>
        ))}
        {busy && <div className="bubble assistant muted">Thinking…</div>}
        <div ref={end} />
      </div>
      {msgs.length === 1 && (
        <div className="chips" aria-label="Examples">
          {EXAMPLES.map((e) => (
            <button key={e} className="chip" onClick={() => send(e)}>
              {e}
            </button>
          ))}
        </div>
      )}
      <form
        className="searchbox"
        onSubmit={(e) => {
          e.preventDefault();
          send(text);
        }}
      >
        <input
          className="input"
          aria-label="Message"
          maxLength={2000}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="What am I currently reading?"
        />
        <button className="btn" disabled={busy || !text.trim()}>
          {busy ? "Thinking…" : "Send"}
        </button>
      </form>
    </main>
  );
}
