/**
 * In-process mock router. Called by the patched api client when USE_MOCKS=true.
 * Returns the same shape as the real Spring Boot endpoints.
 */
import { mockLatency } from "./config";
import { PROJECT, TEMPLATE, assets, runs, uid } from "./db";
import { startRunSimulation } from "./simulator";
import type { PipelineRunDetail } from "@/types/pipeline";

type MockResponse = unknown;

export async function mockRequest(method: string, path: string, body?: unknown): Promise<MockResponse> {
  await mockLatency();

  // ── Projects ──────────────────────────────────────────────────────────────
  if (method === "GET" && path === "/api/v1/projects") {
    return [PROJECT];
  }
  if (method === "POST" && path === "/api/v1/projects") {
    return PROJECT; // always return the same default project
  }

  // ── Pipeline templates ────────────────────────────────────────────────────
  if (method === "GET" && path === "/api/v1/pipeline-templates") {
    return [TEMPLATE];
  }
  if (method === "GET" && path.match(/^\/api\/v1\/pipeline-templates\/.+$/)) {
    return TEMPLATE;
  }

  // ── Assets ────────────────────────────────────────────────────────────────
  if (method === "GET" && path.match(/^\/api\/v1\/projects\/[^/]+\/assets$/)) {
    return assets;
  }
  if (method === "POST" && path.match(/^\/api\/v1\/projects\/[^/]+\/assets\/upload-url$/)) {
    const id = uid();
    return { assetId: id, uploadUrl: "https://mock-r2.example.com/upload", storageKey: `assets/${id}`, expiresAt: new Date(Date.now() + 600_000).toISOString() };
  }
  if (method === "POST" && path.match(/^\/api\/v1\/projects\/[^/]+\/assets\/[^/]+\/confirm-upload$/)) {
    const b = body as { sizeBytes?: number };
    const id = uid();
    const asset = {
      id, projectId: PROJECT.id, name: "uploaded-clip.mp4",
      assetType: "GAMEPLAY_VIDEO" as const, contentType: "video/mp4",
      originalFilename: "uploaded-clip.mp4", sizeBytes: b.sizeBytes ?? null,
      status: "READY" as const, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    };
    assets.unshift(asset);
    return asset;
  }
  if (method === "DELETE" && path.match(/^\/api\/v1\/projects\/[^/]+\/assets\/[^/]+$/)) {
    const assetId = path.split("/").pop()!;
    const i = assets.findIndex((a) => a.id === assetId);
    if (i !== -1) assets.splice(i, 1);
    return undefined;
  }

  // ── Pipeline runs ─────────────────────────────────────────────────────────
  if (method === "GET" && path.match(/^\/api\/v1\/projects\/[^/]+\/pipeline-runs$/)) {
    const sorted = [...runs].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return { content: sorted.map(({ steps: _s, ...r }) => r), page: 0, size: 20, totalElements: sorted.length, totalPages: 1 };
  }

  if (method === "POST" && path.match(/^\/api\/v1\/projects\/[^/]+\/pipeline-runs$/)) {
    const id = uid();
    const newRun: PipelineRunDetail = {
      id, projectId: PROJECT.id, pipelineTemplateId: TEMPLATE.id,
      templateName: "STORY_GAMEPLAY_VIDEO_V1", status: "RUNNING",
      temporalWorkflowId: `story-pipeline-${id}`,
      startedAt: new Date().toISOString(), completedAt: null,
      createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      steps: TEMPLATE.steps.map((d, i) => ({
        id: `${id}-sr-${i + 1}`, stepOrder: d.stepOrder,
        stepHandlerKey: d.stepHandlerKey as PipelineRunDetail["steps"][0]["stepHandlerKey"],
        stepName: d.stepName, status: "PENDING",
        startedAt: null, completedAt: null, attemptNumber: 1, errorMessage: null,
      })),
    };
    runs.unshift(newRun);
    // Kick off the live simulation after a small delay
    setTimeout(() => startRunSimulation(id), 600);
    return newRun;
  }

  if (method === "GET" && path.match(/^\/api\/v1\/projects\/[^/]+\/pipeline-runs\/[^/]+$/)) {
    const runId = path.split("/").pop()!;
    const run = runs.find((r) => r.id === runId);
    if (!run) throw Object.assign(new Error("Run not found"), { status: 404 });
    return run;
  }

  throw Object.assign(new Error(`Mock: no handler for ${method} ${path}`), { status: 404 });
}
