"use client";

import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { PipelineEventSource } from "@/lib/sse/pipelineEventSource";
import { pipelineKeys } from "./usePipelines";
import type { PipelineRunStatus } from "@/types/pipeline";

const TERMINAL_STATUSES: PipelineRunStatus[] = ["COMPLETED", "STEP_FAILED", "CANCELLED"];

/**
 * Opens an SSE connection for a live pipeline run and invalidates TanStack Query
 * on every step event so the run detail page stays up-to-date without polling.
 * Automatically closes once the run reaches a terminal state.
 */
export function usePipelineEvents(
  projectId: string | undefined,
  runId: string | undefined,
  currentStatus: PipelineRunStatus | undefined
) {
  const queryClient = useQueryClient();
  const sourceRef = useRef<PipelineEventSource | null>(null);

  useEffect(() => {
    if (!projectId || !runId) return;
    if (currentStatus && TERMINAL_STATUSES.includes(currentStatus)) return;

    const source = new PipelineEventSource(projectId, runId);
    sourceRef.current = source;

    const unsubEvent = source.onEvent(() => {
      // Invalidate both the individual run and the runs list on any event
      queryClient.invalidateQueries({ queryKey: pipelineKeys.run(projectId, runId) });
      queryClient.invalidateQueries({ queryKey: pipelineKeys.runs(projectId) });
    });

    source.open();

    return () => {
      unsubEvent();
      source.close();
      sourceRef.current = null;
    };
  }, [projectId, runId, currentStatus, queryClient]);
}
