"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { runsApi, templatesApi, type PipelineTemplate } from "@/lib/api/pipelines";
import type {
  CreatePipelineRunRequest,
  PipelineRunDetail,
  PipelineRunSummary,
} from "@/types/pipeline";
import { ensureDefaultProject } from "@/lib/api/projects";

export const pipelineKeys = {
  templates: ["pipeline-templates"] as const,
  runs: (projectId: string) => ["pipeline-runs", projectId] as const,
  run: (projectId: string, runId: string) => ["pipeline-run", projectId, runId] as const,
  defaultProject: ["library", "default-project"] as const,
};

export function useTemplates() {
  return useQuery<PipelineTemplate[]>({
    queryKey: pipelineKeys.templates,
    queryFn: templatesApi.list,
    staleTime: 10 * 60_000, // templates don't change at runtime
  });
}

export function usePipelineRuns(projectId: string | undefined) {
  return useQuery({
    queryKey: pipelineKeys.runs(projectId ?? "none"),
    queryFn: () => runsApi.list(projectId!),
    enabled: !!projectId,
  });
}

export function usePipelineRun(projectId: string | undefined, runId: string | undefined) {
  return useQuery<PipelineRunDetail>({
    queryKey: pipelineKeys.run(projectId ?? "none", runId ?? "none"),
    queryFn: () => runsApi.get(projectId!, runId!),
    enabled: !!projectId && !!runId,
    // SSE events (usePipelineEvents) invalidate this query in real time.
    // Keep a slow fallback poll so the page recovers if the SSE connection is absent.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (!status) return false;
      return status === "RUNNING" || status === "PENDING" ? 15_000 : false;
    },
  });
}

export function useCreatePipelineRun() {
  const queryClient = useQueryClient();

  return useMutation<PipelineRunDetail, Error, { projectId: string; body: CreatePipelineRunRequest }>({
    mutationFn: ({ projectId, body }) => runsApi.create(projectId, body),
    onSuccess: (_data, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: pipelineKeys.runs(projectId) });
    },
  });
}

/** Resolves (or creates) the default project for use in the pipeline create form. */
export function useDefaultProjectForPipeline() {
  return useQuery({
    queryKey: pipelineKeys.defaultProject,
    queryFn: ensureDefaultProject,
    staleTime: 5 * 60_000,
  });
}
