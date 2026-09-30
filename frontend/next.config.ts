import type { NextConfig } from "next";

/** Optional direct API origin (only when NEXT_PUBLIC_API_URL is used instead of the proxy). */
const directApi = process.env.NEXT_PUBLIC_API_URL ?? "";
const isDev = process.env.NODE_ENV !== "production";

const csp = [
  "default-src 'self'",
  // Next injects inline bootstrap scripts; dev mode also needs eval for fast refresh.
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: https://covers.openlibrary.org https://books.google.com https://books.googleusercontent.com",
  "font-src 'self'",
  `connect-src 'self'${directApi ? ` ${directApi}` : ""}${isDev ? " ws:" : ""}`,
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ");

const nextConfig: NextConfig = {
  output: "standalone",
  experimental: {
    // /api/* goes through src/proxy.ts, which buffers bodies; uploads are limited to 25 MB by the backend.
    proxyClientMaxBodySize: "30mb",
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
