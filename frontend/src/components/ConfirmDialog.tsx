"use client";
import { useEffect, useRef, useState } from "react";

/** In-page confirmation (no window.confirm). With `requireText`, the user must type that text first. */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  requireText,
  busy,
  onConfirm,
  onCancel,
  children,
}: {
  open: boolean;
  title: string;
  body: string;
  confirmLabel: string;
  requireText?: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  children?: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [typed, setTyped] = useState("");
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal?.();
    if (!open && d.open) d.close?.();
  }, [open]);
  if (!open) return null;
  const blocked = !!requireText && typed !== requireText;
  return (
    <dialog ref={ref} className="dialog" aria-labelledby="confirm-title" onCancel={onCancel} open>
      <h2 id="confirm-title">{title}</h2>
      <p className="muted">{body}</p>
      {children}
      {requireText && (
        <label className="field">
          Type {requireText} to confirm
          <input className="input" value={typed} onChange={(e) => setTyped(e.target.value)} autoFocus />
        </label>
      )}
      <div className="row justify-end mt-4">
        <button className="btn secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn danger" disabled={blocked || busy} onClick={onConfirm}>
          {busy ? "Working…" : confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
