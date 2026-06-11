/**
 * Drives a RUNNING pipeline run through its 4 steps automatically,
 * emitting SSE-style events to any registered listeners.
 *
 * Timeline (simulated): each step takes 3–6 s, whole run ~18 s.
 */
import type { PipelineRunEvent, PipelineStepName } from "@/types/pipeline";
import { runs } from "./db";

type Listener = (event: PipelineRunEvent) => void;

const listeners = new Map<string, Set<Listener>>();

export function subscribeToRun(runId: string, fn: Listener): () => void {
  if (!listeners.has(runId)) listeners.set(runId, new Set());
  listeners.get(runId)!.add(fn);
  return () => listeners.get(runId)?.delete(fn);
}

function emit(runId: string, event: PipelineRunEvent) {
  listeners.get(runId)?.forEach((fn) => fn(event));
}

const STEP_KEYS: PipelineStepName[] = [
  "GENERATE_STORY",
  "GENERATE_SUBTITLES",
  "RENDER_VIDEO",
  "UPLOAD_VIDEO",
];
const STEP_DURATIONS_MS = [3500, 4000, 6000, 3000];

export function startRunSimulation(runId: string) {
  const run = runs.find((r) => r.id === runId);
  if (!run) return;

  let cursor = Date.now();

  STEP_KEYS.forEach((stepKey, idx) => {
    const startDelay = cursor - Date.now();
    const duration = STEP_DURATIONS_MS[idx];
    cursor += duration;

    setTimeout(() => {
      // Mark step RUNNING in DB
      const step = run.steps[idx];
      step.status = "RUNNING";
      step.startedAt = new Date().toISOString();
      emit(runId, { type: "STEP_STARTED", step: stepKey, timestamp: new Date().toISOString() });
    }, startDelay);

    setTimeout(() => {
      // Mark step COMPLETED in DB
      const step = run.steps[idx];
      step.status = "COMPLETED";
      step.completedAt = new Date().toISOString();
      emit(runId, {
        type: "STEP_COMPLETED",
        step: stepKey,
        artifactId: `artifact-${runId}-${idx}`,
        timestamp: new Date().toISOString(),
      });
    }, startDelay + duration);
  });

  // Complete the run after all steps
  const totalDuration = cursor - Date.now();
  setTimeout(() => {
    run.status = "COMPLETED";
    run.completedAt = new Date().toISOString();
    emit(runId, { type: "RUN_COMPLETED", timestamp: new Date().toISOString() });
  }, totalDuration);
}
