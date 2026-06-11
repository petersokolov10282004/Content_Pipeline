import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "./StatusBadge";

describe("StatusBadge", () => {
  it("renders STEP_FAILED with the friendly label", () => {
    render(<StatusBadge status="STEP_FAILED" />);
    expect(screen.getByText("Failed")).toBeInTheDocument();
  });

  it("renders every documented status without throwing", () => {
    const cases: Array<[Parameters<typeof StatusBadge>[0]["status"], string]> = [
      ["PENDING",        "Pending"],
      ["RUNNING",        "Running"],
      ["AWAITING_INPUT", "Waiting"],
      ["COMPLETED",      "Completed"],
      ["STEP_FAILED",    "Failed"],
      ["FAILED",         "Failed"],
      ["CANCELLED",      "Cancelled"],
      ["SKIPPED",        "Skipped"],
    ];
    for (const [status, label] of cases) {
      const { unmount } = render(<StatusBadge status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    }
  });

  it("falls back gracefully for an unmapped status value", () => {
    render(<StatusBadge status={"WEIRD_NEW_STATUS" as never} />);
    const el = screen.getByText("WEIRD NEW STATUS");
    expect(el.className).toContain("bg-gray-100");
  });

  it("applies the small size variant", () => {
    render(<StatusBadge status="RUNNING" size="sm" />);
    const el = screen.getByText("Running");
    expect(el.className).toContain("text-xs");
  });
});
