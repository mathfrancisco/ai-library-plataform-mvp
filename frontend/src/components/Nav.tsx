"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { BookOpen, Bot, Library, Search, Gauge, Upload, Menu, X, ChevronDown } from "lucide-react";
import { useSignedIn } from "@/lib/auth";
import { useMe } from "@/lib/hooks";
import { logout } from "@/lib/api";

const links = [
  ["/explore", "Explore", Search],
  ["/library", "Library", Library],
  ["/documents", "Documents", Upload],
  ["/ai", "Assistant", Bot],
  ["/dashboard", "Dashboard", Gauge],
] as const;

export function Nav() {
  const signedIn = useSignedIn();
  const me = useMe();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [account, setAccount] = useState(false);
  const close = () => {
    setOpen(false);
    setAccount(false);
  };
  async function signOut() {
    close();
    await logout();
    router.push("/login");
  }
  return (
    <header className="nav">
      <Link href="/" className="brand" onClick={close}>
        <span className="brandmark">
          <BookOpen size={20} />
        </span>
        <span>ShelfMind</span>
      </Link>
      <nav id="main-nav" className={open ? "open" : ""} aria-label="Main">
        {links.map(([href, label, Icon]) => (
          <Link
            href={href}
            key={href}
            aria-current={pathname?.startsWith(href) ? "page" : undefined}
            onClick={close}
          >
            <Icon size={16} />
            {label}
          </Link>
        ))}
      </nav>
      {signedIn ? (
        <div className="account">
          <button
            type="button"
            className="pill"
            aria-haspopup="menu"
            aria-expanded={account}
            onClick={() => setAccount((v) => !v)}
          >
            {me.data?.displayName ?? "Account"}
            <ChevronDown size={14} />
          </button>
          {account && (
            <div className="menu" role="menu">
              <Link role="menuitem" href="/settings" onClick={close}>
                Settings
              </Link>
              <button role="menuitem" onClick={signOut}>
                Sign out
              </button>
            </div>
          )}
        </div>
      ) : (
        <Link className="pill" href="/login" onClick={close}>
          Sign in
        </Link>
      )}
      <button
        type="button"
        className="menubtn"
        aria-label={open ? "Close menu" : "Open menu"}
        aria-expanded={open}
        aria-controls="main-nav"
        onClick={() => setOpen((v) => !v)}
      >
        {open ? <X size={20} /> : <Menu size={20} />}
      </button>
    </header>
  );
}
