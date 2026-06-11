import { USE_MOCKS } from "@/lib/mocks/config";
import { mockRequest } from "@/lib/mocks/handlers";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const DEV_USER_ID = "dev-user-001";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly body?: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // ── Mock path ────────────────────────────────────────────────────────────
  if (USE_MOCKS) {
    const method = (init?.method ?? "GET").toUpperCase();
    const body = init?.body ? JSON.parse(init.body as string) : undefined;
    try {
      return (await mockRequest(method, path, body)) as T;
    } catch (err: unknown) {
      const e = err as { status?: number; message?: string };
      throw new ApiError(e.status ?? 500, e.message ?? "Mock error");
    }
  }

  // ── Real path ─────────────────────────────────────────────────────────────
  const url = `${BASE_URL}${path}`;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Dev-User-Id": DEV_USER_ID,
    ...(init?.headers as Record<string, string>),
  };

  const response = await fetch(url, { ...init, headers });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, `${response.status} ${response.statusText}`, body);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  get:    <T>(path: string)                => request<T>(path, { method: "GET" }),
  post:   <T>(path: string, body?: unknown) => request<T>(path, { method: "POST",   body: body ? JSON.stringify(body) : undefined }),
  put:    <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT",    body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string)                => request<T>(path, { method: "DELETE" }),
};
