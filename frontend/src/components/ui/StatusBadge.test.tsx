import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "./StatusBadge";

/**
 * StatusBadge renders both run-level and step-level statuses through a shared style
 * map. The key risk is an unmapped status value (e.g. a new backend enum) — the badge
 * must fall back to a neutral style and still render its label, never crash.
 */
describe("StatusBadge", () => {
  it("renders a known status with its label, underscores spaced", () => {
    render(<StatusBadge status="STEP_FAILED" />);
    expect(screen.getByText("STEP FAILED")).toBeInTheDocument();
  });

  it("renders every documented run and step status without throwing", () => {
    const statuses = [
      "PENDING",
      "RUNNING",
      "AWAITING_INPUT",
      "COMPLETED",
      "STEP_FAILED",
      "FAILED",
      "CANCELLED",
      "SKIPPED",
    ] as const;
    for (const s of statuses) {
      const { unmount } = render(<StatusBadge status={s} />);
      expect(screen.getByText(s.replace(/_/g, " "))).toBeInTheDocument();
      unmount();
    }
  });

  it("falls back to a neutral style for an unmapped status value", () => {
    // Cast through unknown: simulates a backend value the union doesn't know yet.
    render(<StatusBadge status={"WEIRD_NEW_STATUS" as never} />);
    const el = screen.getByText("WEIRD NEW STATUS");
    expect(el.className).toContain("bg-gray-100");
  });

  it("applies the small size variant", () => {
    render(<StatusBadge status="RUNNING" size="sm" />);
    const el = screen.getByText("RUNNING");
    expect(el.className).toContain("text-xs");
  });
});
