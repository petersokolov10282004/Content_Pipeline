import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { usePipelineEvents } from "./usePipelineEvents";

/**
 * The hook opens a PipelineEventSource for a live run and invalidates TanStack Query
 * on each event. We mock the event-source module so we can assert open/close lifecycle
 * and drive a synthetic event to verify cache invalidation — without a real network.
 */

const openMock = vi.fn();
const closeMock = vi.fn();
let eventCallback: (() => void) | null = null;

vi.mock("@/lib/sse/pipelineEventSource", () => ({
  PipelineEventSource: vi.fn().mockImplementation(() => ({
    open: openMock,
    close: closeMock,
    onEvent: (cb: () => void) => {
      eventCallback = cb;
      return () => {
        eventCallback = null;
      };
    },
  })),
}));

import { PipelineEventSource } from "@/lib/sse/pipelineEventSource";

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe("usePipelineEvents", () => {
  beforeEach(() => {
    openMock.mockClear();
    closeMock.mockClear();
    (PipelineEventSource as unknown as ReturnType<typeof vi.fn>).mockClear();
    eventCallback = null;
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("does nothing without a projectId or runId", () => {
    const client = new QueryClient();
    renderHook(() => usePipelineEvents(undefined, undefined, "RUNNING"), {
      wrapper: wrapper(client),
    });
    expect(PipelineEventSource).not.toHaveBeenCalled();
  });

  it("does not open a stream when the run is already in a terminal state", () => {
    const client = new QueryClient();
    renderHook(() => usePipelineEvents("p", "r", "COMPLETED"), {
      wrapper: wrapper(client),
    });
    expect(PipelineEventSource).not.toHaveBeenCalled();
  });

  it("opens a stream for an active run", () => {
    const client = new QueryClient();
    renderHook(() => usePipelineEvents("p", "r", "RUNNING"), {
      wrapper: wrapper(client),
    });
    expect(PipelineEventSource).toHaveBeenCalledWith("p", "r");
    expect(openMock).toHaveBeenCalledTimes(1);
  });

  it("invalidates the run and runs-list queries when an event arrives", () => {
    const client = new QueryClient();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    renderHook(() => usePipelineEvents("p", "r", "RUNNING"), {
      wrapper: wrapper(client),
    });

    expect(eventCallback).not.toBeNull();
    eventCallback!();

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["pipeline-run", "p", "r"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["pipeline-runs", "p"] });
  });

  it("closes the stream on unmount", () => {
    const client = new QueryClient();
    const { unmount } = renderHook(() => usePipelineEvents("p", "r", "RUNNING"), {
      wrapper: wrapper(client),
    });
    unmount();
    expect(closeMock).toHaveBeenCalledTimes(1);
  });
});
