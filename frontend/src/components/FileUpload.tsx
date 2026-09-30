"use client";
import { useRef, useState } from "react";
import { ACCEPT, checkUpload } from "@/lib/files";

/** Drag-and-drop or picker upload with the same checks the server applies first. */
export function FileUpload({
  onFile,
  busy,
  label = "Upload a permitted document",
  hint = "PDF, EPUB, TXT or Markdown, up to 25 MB. Only upload content you are allowed to use.",
}: {
  onFile: (file: File) => void;
  busy?: boolean;
  label?: string;
  hint?: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [over, setOver] = useState(false);
  function take(file?: File) {
    if (!file) return;
    const error = checkUpload(file);
    setProblem(error);
    if (!error) onFile(file);
  }
  return (
    <div
      className={`upload ${over ? "over" : ""}`}
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        take(e.dataTransfer.files?.[0]);
      }}
    >
      <b>{label}</b>
      <p className="muted">{hint}</p>
      <button type="button" className="btn secondary" disabled={busy} onClick={() => input.current?.click()}>
        {busy ? "Uploading…" : "Choose file"}
      </button>
      <input
        ref={input}
        type="file"
        hidden
        aria-label={label}
        accept={ACCEPT}
        onChange={(e) => {
          take(e.target.files?.[0]);
          e.target.value = "";
        }}
      />
      {problem && (
        <p role="alert" className="errornote mt-3">
          {problem}
        </p>
      )}
    </div>
  );
}
