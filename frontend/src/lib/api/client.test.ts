import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { api, ApiError } from "./client";

/**
 * The API client is the single chokepoint for every backend call: it injects the
 * dev-user header, sets JSON content-type, and turns non-2xx responses into a typed
 * ApiError. These tests stub global fetch and assert the request shape + error mapping.
 */
describe("api client", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  function jsonResponse(body: unknown, init?: { status?: number; statusText?: string; ok?: boolean }) {
    const status = init?.status ?? 200;
    return {
      ok: init?.ok ?? (status >= 200 && status < 300),
      status,
      statusText: init?.statusText ?? "OK",
      json: () => Promise.resolve(body),
    };
  }

  it("GET sends the dev-user header and JSON content-type, and returns parsed JSON", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "p1" }));

    const result = await api.get<{ id: string }>("/api/v1/projects/p1");

    expect(result).toEqual({ id: "p1" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/v1/projects/p1");
    expect(init.method).toBe("GET");
    expect(init.headers["X-Dev-User-Id"]).toBe("dev-user-001");
    expect(init.headers["Content-Type"]).toBe("application/json");
  });

  it("POST serializes the body to JSON", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "new" }));

    await api.post("/api/v1/projects", { name: "X" });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ name: "X" }));
  });

  it("POST with no body sends undefined (not the string 'undefined')", async () => {
    fetchMock.mockResolvedValue(jsonResponse({}));

    await api.post("/api/v1/projects/p1/assets/a1/confirm-upload");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.body).toBeUndefined();
  });

  it("returns undefined for a 204 No Content response without calling json()", async () => {
    const json = vi.fn();
    fetchMock.mockResolvedValue({ ok: true, status: 204, statusText: "No Content", json });

    const result = await api.delete("/api/v1/projects/p1");

    expect(result).toBeUndefined();
    expect(json).not.toHaveBeenCalled();
  });

  it("throws a typed ApiError carrying status and parsed error body on non-2xx", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ message: "not found" }, { status: 404, statusText: "Not Found" })
    );

    await expect(api.get("/api/v1/projects/missing")).rejects.toMatchObject({
      name: "ApiError",
      status: 404,
      body: { message: "not found" },
    });
  });

  it("still throws ApiError when the error body is not valid JSON", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: "Server Error",
      json: () => Promise.reject(new Error("not json")),
    });

    const err = (await api.get("/api/v1/boom").catch((e) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(500);
    expect(err.body).toBeNull();
  });
});
