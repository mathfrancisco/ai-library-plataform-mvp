import { NextResponse, type NextRequest } from "next/server";

/**
 * Same-origin API proxy (SPEC-08 §4). Runs at request time, so API_INTERNAL_URL is a runtime setting
 * (next.config rewrites would be frozen into the build). Upload bodies up to 30 MB pass through; see
 * experimental.proxyClientMaxBodySize in next.config.ts.
 */
export function proxy(request: NextRequest) {
  const target = new URL(
    request.nextUrl.pathname + request.nextUrl.search,
    process.env.API_INTERNAL_URL ?? "http://localhost:8080",
  );
  return NextResponse.rewrite(target);
}

export const config = { matcher: "/api/:path*" };
