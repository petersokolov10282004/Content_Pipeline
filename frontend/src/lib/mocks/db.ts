/**
 * In-memory store. Seeded once at module load; mutations are reflected
 * immediately (no persistence across page reloads — intentional for dev).
 */
import type { Project } from "@/types/project";
import type { Asset } from "@/types/asset";
import type { PipelineRunDetail, PipelineRunSummary, PipelineStepState } from "@/types/pipeline";
import type { PipelineTemplate } from "@/lib/api/pipelines";

// ─── Helpers ──────────────────────────────────────────────────────────────────

let _seq = 1;
export function uid(): string {
  return `mock-${String(_seq++).padStart(4, "0")}-${Math.random().toString(36).slice(2, 6)}`;
}

function iso(offsetMs = 0): string {
  return new Date(Date.now() + offsetMs).toISOString();
}

// ─── Seed data ────────────────────────────────────────────────────────────────

export const PROJECT: Project = {
  id: "proj-0001",
  name: "My Library",
  description: "Default dev project",
  ownerIdentifier: "dev-user-001",
  status: "ACTIVE",
  assetCount: 2,
  createdAt: iso(-7 * 86_400_000),
  updatedAt: iso(-86_400_000),
};

export const TEMPLATE: PipelineTemplate = {
  id: "tpl-0001",
  name: "STORY_GAMEPLAY_VIDEO_V1",
  description: "Short-form story gameplay video: AI script → AI subtitles → FFmpeg render → YouTube upload",
  version: 1,
  active: true,
  steps: [
    { id: "step-def-1", stepOrder: 1, stepHandlerKey: "GENERATE_STORY",     stepName: "Generate Story Script",  description: null },
    { id: "step-def-2", stepOrder: 2, stepHandlerKey: "GENERATE_SUBTITLES", stepName: "Generate Subtitles",     description: null },
    { id: "step-def-3", stepOrder: 3, stepHandlerKey: "RENDER_VIDEO",       stepName: "Render Video",           description: null },
    { id: "step-def-4", stepOrder: 4, stepHandlerKey: "UPLOAD_VIDEO",       stepName: "Upload to YouTube",      description: null },
  ],
};

// Mutable asset list
export const assets: Asset[] = [
  {
    id: "asset-0001",
    projectId: PROJECT.id,
    name: "subway-surf-clip.mp4",
    assetType: "GAMEPLAY_VIDEO",
    contentType: "video/mp4",
    originalFilename: "subway-surf-clip.mp4",
    sizeBytes: 48_231_488,
    status: "READY",
    createdAt: iso(-3 * 86_400_000),
    updatedAt: iso(-3 * 86_400_000),
  },
  {
    id: "asset-0002",
    projectId: PROJECT.id,
    name: "minecraft-freerun.mp4",
    assetType: "GAMEPLAY_VIDEO",
    contentType: "video/mp4",
    originalFilename: "minecraft-freerun.mp4",
    sizeBytes: 72_940_544,
    status: "READY",
    createdAt: iso(-5 * 86_400_000),
    updatedAt: iso(-5 * 86_400_000),
  },
];

function makeSteps(runId: string, status: "PENDING" | "COMPLETED" | "RUNNING" | "STEP_FAILED"): PipelineStepState[] {
  const defs = TEMPLATE.steps;
  if (status === "PENDING") {
    return defs.map((d, i) => ({
      id: `${runId}-sr-${i + 1}`, stepOrder: d.stepOrder,
      stepHandlerKey: d.stepHandlerKey as PipelineStepState["stepHandlerKey"],
      stepName: d.stepName, status: "PENDING",
      startedAt: null, completedAt: null, attemptNumber: 1, errorMessage: null,
    }));
  }
  if (status === "COMPLETED") {
    return defs.map((d, i) => ({
      id: `${runId}-sr-${i + 1}`, stepOrder: d.stepOrder,
      stepHandlerKey: d.stepHandlerKey as PipelineStepState["stepHandlerKey"],
      stepName: d.stepName, status: "COMPLETED",
      startedAt: iso(-(4 - i) * 12_000), completedAt: iso(-(4 - i) * 8_000),
      attemptNumber: 1, errorMessage: null,
    }));
  }
  if (status === "STEP_FAILED") {
    return defs.map((d, i) => ({
      id: `${runId}-sr-${i + 1}`, stepOrder: d.stepOrder,
      stepHandlerKey: d.stepHandlerKey as PipelineStepState["stepHandlerKey"],
      stepName: d.stepName,
      status: i < 2 ? "COMPLETED" : i === 2 ? "FAILED" : "PENDING",
      startedAt: i <= 2 ? iso(-(4 - i) * 12_000) : null,
      completedAt: i < 2 ? iso(-(4 - i) * 8_000) : null,
      attemptNumber: 1,
      errorMessage: i === 2 ? "R2 credentials not configured — render step skipped" : null,
    }));
  }
  // RUNNING — first 2 done, step 3 in progress, step 4 pending
  return defs.map((d, i) => ({
    id: `${runId}-sr-${i + 1}`, stepOrder: d.stepOrder,
    stepHandlerKey: d.stepHandlerKey as PipelineStepState["stepHandlerKey"],
    stepName: d.stepName,
    status: i < 2 ? "COMPLETED" : i === 2 ? "RUNNING" : "PENDING",
    startedAt: i <= 2 ? iso(-(4 - i) * 12_000) : null,
    completedAt: i < 2 ? iso(-(4 - i) * 8_000) : null,
    attemptNumber: 1, errorMessage: null,
  }));
}

// Mutable runs list (most-recent-first)
export const runs: PipelineRunDetail[] = [
  {
    id: "run-0001", projectId: PROJECT.id, pipelineTemplateId: TEMPLATE.id,
    templateName: "STORY_GAMEPLAY_VIDEO_V1", status: "COMPLETED",
    temporalWorkflowId: "story-pipeline-run-0001",
    startedAt: iso(-2 * 86_400_000), completedAt: iso(-2 * 86_400_000 + 52_000),
    createdAt: iso(-2 * 86_400_000), updatedAt: iso(-2 * 86_400_000 + 52_000),
    steps: makeSteps("run-0001", "COMPLETED"),
  },
  {
    id: "run-0002", projectId: PROJECT.id, pipelineTemplateId: TEMPLATE.id,
    templateName: "STORY_GAMEPLAY_VIDEO_V1", status: "STEP_FAILED",
    temporalWorkflowId: "story-pipeline-run-0002",
    startedAt: iso(-86_400_000), completedAt: iso(-86_400_000 + 31_000),
    createdAt: iso(-86_400_000), updatedAt: iso(-86_400_000 + 31_000),
    steps: makeSteps("run-0002", "STEP_FAILED"),
  },
];
