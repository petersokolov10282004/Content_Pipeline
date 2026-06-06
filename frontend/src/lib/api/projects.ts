import { api } from "./client";
import type {
  Project,
  CreateProjectRequest,
  UpdateProjectRequest,
} from "@/types/project";

const BASE = "/api/v1/projects";

export const projectsApi = {
  list: () => api.get<Project[]>(BASE),
  get: (projectId: string) => api.get<Project>(`${BASE}/${projectId}`),
  create: (body: CreateProjectRequest) => api.post<Project>(BASE, body),
  update: (projectId: string, body: UpdateProjectRequest) =>
    api.put<Project>(`${BASE}/${projectId}`, body),
  remove: (projectId: string) => api.delete<void>(`${BASE}/${projectId}`),
};

/** Name of the implicit project the library uploads into for the dev user. */
export const DEFAULT_LIBRARY_PROJECT_NAME = "My Library";

/**
 * Phase 2 has no project selector UI, but assets must live under a project.
 * Resolve (or lazily create) a single default project to host the library.
 * Replaced by real project selection in a later phase.
 */
export async function ensureDefaultProject(): Promise<Project> {
  const projects = await projectsApi.list();
  const existing = projects.find(
    (p) => p.status === "ACTIVE" && p.name === DEFAULT_LIBRARY_PROJECT_NAME
  );
  if (existing) return existing;

  const active = projects.find((p) => p.status === "ACTIVE");
  if (active) return active;

  return projectsApi.create({
    name: DEFAULT_LIBRARY_PROJECT_NAME,
    description: "Default project for the gameplay library.",
  });
}
