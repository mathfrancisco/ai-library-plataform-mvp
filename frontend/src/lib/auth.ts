"use client";
import { useSyncExternalStore } from "react";
const A = "ailib.access",
  R = "ailib.refresh",
  EVENT = "ailib:auth";
function read(key: string) {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function notify() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(EVENT));
}
export const auth = {
  access: () => read(A),
  refresh: () => read(R),
  save: (accessToken: string, refreshToken: string) => {
    localStorage.setItem(A, accessToken);
    localStorage.setItem(R, refreshToken);
    notify();
  },
  clear: () => {
    localStorage.removeItem(A);
    localStorage.removeItem(R);
    notify();
  },
};
function subscribe(cb: () => void) {
  window.addEventListener(EVENT, cb);
  window.addEventListener("storage", cb);
  return () => {
    window.removeEventListener(EVENT, cb);
    window.removeEventListener("storage", cb);
  };
}
/** True when a session token is stored; false during SSR. */
export function useSignedIn() {
  return useSyncExternalStore(
    subscribe,
    () => auth.access() !== null,
    () => false,
  );
}

const noopSubscribe = () => () => {};
/** False during SSR and the first hydration pass, true afterwards; lets guards wait before redirecting. */
export function useSessionKnown() {
  return useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );
}
