import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * Auth middleware stub.
 * Currently passes all requests through.
 *
 * When real auth is added:
 *   1. Install next-auth
 *   2. Check for a valid session cookie here
 *   3. Redirect unauthenticated requests to /login
 *   Example:
 *     const session = await getToken({ req });
 *     if (!session && !req.nextUrl.pathname.startsWith('/login')) {
 *       return NextResponse.redirect(new URL('/login', req.url));
 *     }
 */
export function middleware(_request: NextRequest) {
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
