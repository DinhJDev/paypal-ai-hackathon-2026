import { NextRequest, NextResponse } from "next/server";
import { getAuth0 } from "@/lib/auth0";

export async function proxy(request: NextRequest) {
  const auth0 = getAuth0();
  if (auth0) return auth0.middleware(request);
  if (request.nextUrl.pathname === "/auth" || request.nextUrl.pathname.startsWith("/auth/")) {
    return new NextResponse("Sign-in is temporarily unavailable.", {
      status: 503, headers: { "Cache-Control": "private, no-store" },
    });
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt).*)"],
};
