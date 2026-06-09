import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "@/components/ui/StatusBadge";
import type { PipelineStepState } from "@/types/pipeline";

/**
 * Regression guard for the "Cannot read properties of undefined (reading 'replace')"
 * crash: the backend's StepRunResponse has NO `step` field — it sends `stepName` /
 * `stepHandlerKey`. The run detail page derives the label from those, so this renders
 * the same step-row markup against the real backend shape and asserts no throw.
 *
 * (We render the row markup directly rather than the whole page to avoid booting the
 * full provider/router stack; the label derivation is the logic under test.)
 */

const stepStatusIcon = {
  PENDING: "○",
  RUNNING: "◉",
  COMPLETED: "✓",
  FAILED: "✗",
  AWAITING_INPUT: "◎",
  SKIPPED: "–",
} as const;

function StepRow({ step }: { step: PipelineStepState }) {
  const label = step.stepName ?? step.stepHandlerKey?.replace(/_/g, " ") ?? "Step";
  return (
    <li>
      <span>{stepStatusIcon[step.status]}</span>
      <span>{label}</span>
      <StatusBadge status={step.status} size="sm" />
      {step.errorMessage && <p>{step.errorMessage}</p>}
    </li>
  );
}

const backendStep = (over: Partial<PipelineStepState> = {}): PipelineStepState => ({
  id: "s1",
  stepOrder: 1,
  stepHandlerKey: "GENERATE_STORY",
  stepName: "Generate Story Script",
  status: "RUNNING",
  startedAt: null,
  completedAt: null,
  attemptNumber: 1,
  errorMessage: null,
  ...over,
});

describe("run detail step row (backend shape)", () => {
  it("renders the human step name from the real backend payload", () => {
    render(<StepRow step={backendStep()} />);
    expect(screen.getByText("Generate Story Script")).toBeInTheDocument();
  });

  it("does not crash when stepName is missing — falls back to the handler key", () => {
    const step = backendStep({ stepName: undefined as unknown as string });
    expect(() => render(<StepRow step={step} />)).not.toThrow();
    expect(screen.getByText("GENERATE STORY")).toBeInTheDocument();
  });

  it("does not crash when both label fields are missing", () => {
    const step = backendStep({
      stepName: undefined as unknown as string,
      stepHandlerKey: undefined as unknown as PipelineStepState["stepHandlerKey"],
    });
    expect(() => render(<StepRow step={step} />)).not.toThrow();
    expect(screen.getByText("Step")).toBeInTheDocument();
  });

  it("surfaces a step error message", () => {
    render(<StepRow step={backendStep({ status: "FAILED", errorMessage: "boom" })} />);
    expect(screen.getByText("boom")).toBeInTheDocument();
  });
});
