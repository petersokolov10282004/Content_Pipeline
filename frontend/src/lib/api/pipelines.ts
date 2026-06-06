import { api } from "./client";
import type {
  CreatePipelineRunRequest,
  PipelineRunDetail,
  PipelineRunSummary,
} from "@/types/pipeline";

export interface PipelineTemplate {
  id: string;
  name: string;
  description: string | null;
  version: number;
  active: boolean;
  steps: Array<{
    id: string;
    stepOrder: number;
    stepHandlerKey: string;
    stepName: string;
    description: string | null;
  }>;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const templatesApi = {
  list: () => api.get<PipelineTemplate[]>("/api/v1/pipeline-templates"),
  get: (id: string) => api.get<PipelineTemplate>(`/api/v1/pipeline-templates/${id}`),
};

const runsBase = (projectId: string) =>
  `/api/v1/projects/${projectId}/pipeline-runs`;

export const runsApi = {
  list: (projectId: string, page = 0, size = 20) =>
    api.get<PagedResponse<PipelineRunSummary>>(
      `${runsBase(projectId)}?page=${page}&size=${size}`
    ),
  get: (projectId: string, runId: string) =>
    api.get<PipelineRunDetail>(`${runsBase(projectId)}/${runId}`),
  create: (projectId: string, body: CreatePipelineRunRequest) =>
    api.post<PipelineRunDetail>(runsBase(projectId), body),
};
