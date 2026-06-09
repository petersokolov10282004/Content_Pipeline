import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("./client", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { inferAssetType, putToPresignedUrl } from "./assets";

describe("inferAssetType", () => {
  const asFile = (type: string) => ({ type }) as File;

  it("maps video MIME types to GAMEPLAY_VIDEO", () => {
    expect(inferAssetType(asFile("video/mp4"))).toBe("GAMEPLAY_VIDEO");
  });

  it("maps audio MIME types to BACKGROUND_MUSIC", () => {
    expect(inferAssetType(asFile("audio/mpeg"))).toBe("BACKGROUND_MUSIC");
  });

  it("maps image MIME types to IMAGE", () => {
    expect(inferAssetType(asFile("image/png"))).toBe("IMAGE");
  });

  it("falls back to OTHER for unknown MIME types", () => {
    expect(inferAssetType(asFile("application/pdf"))).toBe("OTHER");
    expect(inferAssetType(asFile(""))).toBe("OTHER");
  });
});

/**
 * putToPresignedUrl uploads raw bytes via XMLHttpRequest (for progress events),
 * bypassing the API client. We install a fake XHR to assert the method/header,
 * progress reporting, and resolve/reject behavior across status codes.
 */
describe("putToPresignedUrl", () => {
  class FakeXHR {
    static last: FakeXHR;
    method = "";
    url = "";
    headers: Record<string, string> = {};
    status = 200;
    statusText = "OK";
    sent: unknown = null;
    upload: { onprogress: ((e: ProgressEvent) => void) | null } = { onprogress: null };
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;

    constructor() {
      FakeXHR.last = this;
    }
    open(method: string, url: string) {
      this.method = method;
      this.url = url;
    }
    setRequestHeader(k: string, v: string) {
      this.headers[k] = v;
    }
    send(body: unknown) {
      this.sent = body;
    }
  }

  beforeEach(() => {
    vi.stubGlobal("XMLHttpRequest", FakeXHR as unknown as typeof XMLHttpRequest);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("PUTs the file with the signed content-type and resolves on 2xx", async () => {
    const file = new File(["data"], "clip.mp4", { type: "video/mp4" });
    const promise = putToPresignedUrl("https://r2/put", file, "video/mp4");

    const xhr = FakeXHR.last;
    expect(xhr.method).toBe("PUT");
    expect(xhr.url).toBe("https://r2/put");
    expect(xhr.headers["Content-Type"]).toBe("video/mp4");
    expect(xhr.sent).toBe(file);

    xhr.status = 200;
    xhr.onload!();
    await expect(promise).resolves.toBeUndefined();
  });

  it("reports upload progress as a 0..1 fraction", async () => {
    const file = new File(["data"], "clip.mp4", { type: "video/mp4" });
    const onProgress = vi.fn();
    const promise = putToPresignedUrl("https://r2/put", file, "video/mp4", onProgress);

    const xhr = FakeXHR.last;
    xhr.upload.onprogress!({ lengthComputable: true, loaded: 50, total: 200 } as ProgressEvent);
    expect(onProgress).toHaveBeenCalledWith(0.25);

    xhr.status = 204;
    xhr.onload!();
    await promise;
  });

  it("rejects when the upload returns a non-2xx status", async () => {
    const file = new File(["data"], "clip.mp4", { type: "video/mp4" });
    const promise = putToPresignedUrl("https://r2/put", file, "video/mp4");

    const xhr = FakeXHR.last;
    xhr.status = 403;
    xhr.statusText = "Forbidden";
    xhr.onload!();

    await expect(promise).rejects.toThrow(/403/);
  });

  it("rejects on a network error", async () => {
    const file = new File(["data"], "clip.mp4", { type: "video/mp4" });
    const promise = putToPresignedUrl("https://r2/put", file, "video/mp4");

    FakeXHR.last.onerror!();

    await expect(promise).rejects.toThrow(/network error/);
  });
});
