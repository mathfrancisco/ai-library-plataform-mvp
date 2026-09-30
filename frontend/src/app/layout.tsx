import "./globals.css";
import type { Metadata } from "next";
import localFont from "next/font/local";
import { Nav } from "@/components/Nav";
import { QueryProvider } from "@/lib/query";
import { ToastProvider } from "@/components/Toast";

// Self-hosted variable Inter (no request to Google at build or run time).
const inter = localFont({
  src: "../../node_modules/@fontsource-variable/inter/files/inter-latin-wght-normal.woff2",
  variable: "--font-sans",
  display: "swap",
});

export const metadata: Metadata = {
  title: { default: "ShelfMind — AI Library", template: "%s · ShelfMind" },
  description: "Search, understand and explore books with grounded AI.",
  icons: { icon: "/favicon.svg" },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable}>
      <body>
        <QueryProvider>
          <ToastProvider>
            <a href="#main" className="skiplink">
              Skip to content
            </a>
            <Nav />
            <div id="main">{children}</div>
            <footer className="footer">
              ShelfMind · AI Library portfolio project · Metadata-first, rights-aware RAG.
            </footer>
          </ToastProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
