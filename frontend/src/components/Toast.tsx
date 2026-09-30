"use client";
import { createContext, useCallback, useContext, useState, type ReactNode } from "react";

type Toast = {
  id: number;
  kind: "success" | "error";
  text: string;
  action?: { label: string; run: () => void };
};
type Push = (t: Omit<Toast, "id">) => void;

const ToastContext = createContext<Push>(() => {});

/** Tiny toast stack: success/error feedback with an optional action (e.g. Undo). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback<Push>((t) => {
    const id = Date.now() + Math.random();
    setToasts((v) => [...v, { ...t, id }]);
    setTimeout(() => setToasts((v) => v.filter((x) => x.id !== id)), 6000);
  }, []);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`} role={t.kind === "error" ? "alert" : "status"}>
            <span>{t.text}</span>
            {t.action && (
              <button
                className="linkbtn"
                onClick={() => {
                  t.action!.run();
                  setToasts((v) => v.filter((x) => x.id !== t.id));
                }}
              >
                {t.action.label}
              </button>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
