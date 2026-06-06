"use client";

import { createContext, useContext, type ReactNode } from "react";
import { DEV_USER, type AppUser } from "./devUser";

type SessionState = {
  user: AppUser | null;
  isLoading: boolean;
};

const SessionContext = createContext<SessionState>({
  user: null,
  isLoading: false,
});

/**
 * Auth provider abstraction.
 *
 * Today: always provides DEV_USER. No network calls, no redirects.
 * When real auth is added:
 *   - Replace this provider's value with the NextAuth session
 *   - Every consumer continues to call useSession() from this file — no page changes needed
 *   - Update lib/api/client.ts to attach the JWT from the session
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const authEnabled = process.env.NEXT_PUBLIC_AUTH_ENABLED === "true";

  if (!authEnabled) {
    return (
      <SessionContext.Provider value={{ user: DEV_USER, isLoading: false }}>
        {children}
      </SessionContext.Provider>
    );
  }

  // When auth is enabled, replace this with real NextAuth SessionProvider logic.
  // For now it falls through to dev user so nothing breaks.
  return (
    <SessionContext.Provider value={{ user: DEV_USER, isLoading: false }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession(): SessionState {
  return useContext(SessionContext);
}

export function useRequireAuth(): AppUser {
  const { user, isLoading } = useSession();
  if (!isLoading && !user) {
    // When real auth is added: redirect to /login here
    throw new Error("Not authenticated");
  }
  return user!;
}
