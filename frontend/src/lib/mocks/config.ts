/**
 * Standalone "fake backend" toggle.
 *
 * When enabled, the API client and the SSE source serve data from an in-memory
 * store (lib/mocks/*) instead of hitting the Spring Boot backend — so the whole
 * frontend runs, and can be visually validated, with no Java/Docker stack.
 *
 * Enabled by default. Set NEXT_PUBLIC_USE_MOCKS=false to talk to the real API.
 */
export const USE_MOCKS = process.env.NEXT_PUBLIC_USE_MOCKS !== "false";

/** Simulated network latency so loading states are exercised, not skipped. */
export function mockLatency(min = 140, max = 320): Promise<void> {
  const ms = Math.round(min + Math.random() * (max - min));
  return new Promise((resolve) => setTimeout(resolve, ms));
}
