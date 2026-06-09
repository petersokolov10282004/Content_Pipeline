import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { PipelineEventSource } from "./pipelineEventSource";
import type { PipelineRunEvent } from "@/types/pipeline";

/**
 * jsdom has no EventSource, so we install a controllable fake that records the URL,
 * lets tests fire named events / errors, and tracks close(). This exercises the
 * dispatch path, terminal-event auto-close, reconnect-on-error backoff, and the
 * listener add/remove contract — the area most likely to surface a runtime exception.
 */

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  url: string;
  onerror: ((this: EventSource, ev: Event) => void) | null = null;
  closed = false;
  private handlers: Record<string, ((e: MessageEvent) => void)[]> = {};

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, fn: (e: MessageEvent) => void) {
    (this.handlers[type] ??= []).push(fn);
  }

  close() {
    this.closed = true;
  }

  // ---- test helpers ----
  emit(type: string, data: unknown) {
    const event = { data: JSON.stringify(data) } as MessageEvent;
    (this.handlers[type] ?? []).forEach((fn) => fn(event));
  }

  emitRaw(type: string, raw: string) {
    (this.handlers[type] ?? []).forEach((fn) => fn({ data: raw } as MessageEvent));
  }

  fireError() {
    this.onerror?.call(this as unknown as EventSource, new Event("error"));
  }

  static latest() {
    return this.instances[this.instances.length - 1];
  }

  static reset() {
    this.instances = [];
  }
}

beforeEach(() => {
  FakeEventSource.reset();
  vi.stubGlobal("EventSource", FakeEventSource as unknown as typeof EventSource);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("PipelineEventSource", () => {
  it("opens an EventSource at the run's events URL", () => {
    const src = new PipelineEventSource("proj-1", "run-1");
    src.open();
    expect(FakeEventSource.latest().url).toContain(
      "/api/v1/projects/proj-1/pipeline-runs/run-1/events"
    );
  });

  it("delivers parsed pipeline-event payloads to registered listeners", () => {
    const src = new PipelineEventSource("p", "r");
    const received: PipelineRunEvent[] = [];
    src.onEvent((e) => received.push(e));
    src.open();

    FakeEventSource.latest().emit("pipeline-event", {
      type: "STEP_STARTED",
      step: "GENERATE_STORY",
      timestamp: "2026-06-09T00:00:00Z",
    });

    expect(received).toHaveLength(1);
    expect(received[0]).toMatchObject({ type: "STEP_STARTED", step: "GENERATE_STORY" });
  });

  it("ignores malformed JSON without throwing or notifying listeners", () => {
    const src = new PipelineEventSource("p", "r");
    const listener = vi.fn();
    src.onEvent(listener);
    src.open();

    expect(() => FakeEventSource.latest().emitRaw("pipeline-event", "{not json")).not.toThrow();
    expect(listener).not.toHaveBeenCalled();
  });

  it("closes the stream after a terminal RUN_COMPLETED event", () => {
    const src = new PipelineEventSource("p", "r");
    src.open();
    const es = FakeEventSource.latest();

    es.emit("pipeline-event", { type: "RUN_COMPLETED", timestamp: "t" });

    expect(es.closed).toBe(true);
  });

  it("closes the stream after a terminal RUN_FAILED event", () => {
    const src = new PipelineEventSource("p", "r");
    src.open();
    const es = FakeEventSource.latest();

    es.emit("pipeline-event", { type: "RUN_FAILED", error: "boom", timestamp: "t" });

    expect(es.closed).toBe(true);
  });

  it("reconnects with backoff after an error, opening a fresh EventSource", () => {
    vi.useFakeTimers();
    const src = new PipelineEventSource("p", "r");
    src.open();
    expect(FakeEventSource.instances).toHaveLength(1);

    FakeEventSource.latest().fireError();
    // First retry is scheduled at 1000ms
    vi.advanceTimersByTime(1000);

    expect(FakeEventSource.instances).toHaveLength(2);
  });

  it("does not reconnect once close() has been called", () => {
    vi.useFakeTimers();
    const src = new PipelineEventSource("p", "r");
    src.open();
    const es = FakeEventSource.latest();

    src.close();
    es.fireError();
    vi.advanceTimersByTime(60_000);

    // Still only the original instance — no reconnect after manual close.
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(es.closed).toBe(true);
  });

  it("fires close listeners when the stream is closed", () => {
    const src = new PipelineEventSource("p", "r");
    const onClose = vi.fn();
    src.onClose(onClose);
    src.open();

    src.close();

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("onEvent returns an unsubscribe that detaches the listener", () => {
    const src = new PipelineEventSource("p", "r");
    const listener = vi.fn();
    const unsub = src.onEvent(listener);
    src.open();

    unsub();
    FakeEventSource.latest().emit("pipeline-event", { type: "RUN_COMPLETED", timestamp: "t" });

    expect(listener).not.toHaveBeenCalled();
  });

  it("open() is a no-op after the source has been closed", () => {
    const src = new PipelineEventSource("p", "r");
    src.close();
    src.open();
    expect(FakeEventSource.instances).toHaveLength(0);
  });
});
