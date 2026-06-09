import { describe, it, expect } from "vitest";
import { pipelineKeys } from "./usePipelines";

/**
 * usePipelineEvents and the query hooks must agree on TanStack Query keys, or
 * invalidation silently misses. These tests pin the key shapes so a rename can't
 * drift the two apart unnoticed.
 */
describe("pipelineKeys", () => {
  it("templates key is a stable constant", () => {
    expect(pipelineKeys.templates).toEqual(["pipeline-templates"]);
  });

  it("runs key namespaces by project", () => {
    expect(pipelineKeys.runs("proj-1")).toEqual(["pipeline-runs", "proj-1"]);
  });

  it("run key namespaces by project and run", () => {
    expect(pipelineKeys.run("proj-1", "run-9")).toEqual(["pipeline-run", "proj-1", "run-9"]);
  });

  it("run and runs keys share a project segment but differ in prefix", () => {
    const run = pipelineKeys.run("p", "r");
    const runs = pipelineKeys.runs("p");
    expect(run[0]).not.toBe(runs[0]);
    expect(run[1]).toBe(runs[1]);
  });
});
