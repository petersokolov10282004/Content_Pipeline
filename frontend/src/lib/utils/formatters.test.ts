import { describe, it, expect, vi, afterEach } from "vitest";
import {
  formatDuration,
  formatFileSize,
  formatDate,
  formatRelative,
} from "./formatters";

describe("formatDuration", () => {
  it("formats sub-minute durations with a zero-padded seconds field", () => {
    expect(formatDuration(5)).toBe("0:05");
    expect(formatDuration(59)).toBe("0:59");
  });

  it("formats whole and partial minutes", () => {
    expect(formatDuration(60)).toBe("1:00");
    expect(formatDuration(90)).toBe("1:30");
    expect(formatDuration(605)).toBe("10:05");
  });

  it("handles zero", () => {
    expect(formatDuration(0)).toBe("0:00");
  });
});

describe("formatFileSize", () => {
  it("reports raw bytes below 1 KiB", () => {
    expect(formatFileSize(0)).toBe("0 B");
    expect(formatFileSize(1023)).toBe("1023 B");
  });

  it("switches to KB at the 1 KiB boundary", () => {
    expect(formatFileSize(1024)).toBe("1.0 KB");
    expect(formatFileSize(1536)).toBe("1.5 KB");
  });

  it("switches to MB at the 1 MiB boundary", () => {
    expect(formatFileSize(1024 * 1024)).toBe("1.0 MB");
    expect(formatFileSize(5 * 1024 * 1024)).toBe("5.0 MB");
  });

  it("switches to GB at the 1 GiB boundary with two decimals", () => {
    expect(formatFileSize(1024 * 1024 * 1024)).toBe("1.00 GB");
    expect(formatFileSize(3 * 1024 * 1024 * 1024)).toBe("3.00 GB");
  });
});

describe("formatRelative", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns 'just now' for timestamps under a minute old", () => {
    const now = new Date("2026-06-09T12:00:00Z");
    vi.useFakeTimers();
    vi.setSystemTime(now);
    expect(formatRelative("2026-06-09T11:59:30Z")).toBe("just now");
  });

  it("reports minutes, hours, and days as the gap widens", () => {
    const now = new Date("2026-06-09T12:00:00Z");
    vi.useFakeTimers();
    vi.setSystemTime(now);
    expect(formatRelative("2026-06-09T11:30:00Z")).toBe("30m ago");
    expect(formatRelative("2026-06-09T09:00:00Z")).toBe("3h ago");
    expect(formatRelative("2026-06-07T12:00:00Z")).toBe("2d ago");
  });
});

describe("formatDate", () => {
  it("renders a human-readable date for a valid ISO string", () => {
    // Locale formatting varies by environment; assert it contains the parts we control.
    const out = formatDate("2026-06-09T12:00:00Z");
    expect(out).toMatch(/2026/);
    expect(out).toMatch(/Jun/);
  });
});
