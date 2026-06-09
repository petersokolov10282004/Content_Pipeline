import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { Project } from "@/types/project";

/**
 * ensureDefaultProject is the dev-mode project resolver: prefer the named library
 * project, else any active project, else create one. We mock the projectsApi to drive
 * each branch and confirm create() is only called as a last resort.
 */

const listMock = vi.fn();
const createMock = vi.fn();

vi.mock("./client", () => ({
  api: {
    get: (...a: unknown[]) => listMock(...a),
    post: (...a: unknown[]) => createMock(...a),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import { ensureDefaultProject, DEFAULT_LIBRARY_PROJECT_NAME } from "./projects";

function project(p: Partial<Project>): Project {
  return {
    id: "id",
    name: "n",
    description: null,
    ownerIdentifier: "dev-user-001",
    status: "ACTIVE",
    assetCount: 0,
    createdAt: "",
    updatedAt: "",
    ...p,
  };
}

describe("ensureDefaultProject", () => {
  beforeEach(() => {
    listMock.mockReset();
    createMock.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("returns the named library project when one exists", async () => {
    const lib = project({ id: "lib", name: DEFAULT_LIBRARY_PROJECT_NAME, status: "ACTIVE" });
    listMock.mockResolvedValue([project({ id: "other", name: "Other" }), lib]);

    const result = await ensureDefaultProject();

    expect(result.id).toBe("lib");
    expect(createMock).not.toHaveBeenCalled();
  });

  it("falls back to any active project when the named one is absent", async () => {
    listMock.mockResolvedValue([
      project({ id: "archived", name: "Old", status: "ARCHIVED" }),
      project({ id: "active", name: "Some Active" }),
    ]);

    const result = await ensureDefaultProject();

    expect(result.id).toBe("active");
    expect(createMock).not.toHaveBeenCalled();
  });

  it("creates a new library project when none are usable", async () => {
    listMock.mockResolvedValue([project({ id: "archived", status: "ARCHIVED" })]);
    createMock.mockResolvedValue(project({ id: "created", name: DEFAULT_LIBRARY_PROJECT_NAME }));

    const result = await ensureDefaultProject();

    expect(result.id).toBe("created");
    expect(createMock).toHaveBeenCalledTimes(1);
  });

  it("creates a new project when the list is empty", async () => {
    listMock.mockResolvedValue([]);
    createMock.mockResolvedValue(project({ id: "created" }));

    await ensureDefaultProject();

    expect(createMock).toHaveBeenCalledTimes(1);
  });
});
