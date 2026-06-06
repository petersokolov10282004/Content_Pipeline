import { cn } from "@/lib/utils/cn";
import type { PipelineRunStatus, StepRunStatus } from "@/types/pipeline";

type Status = PipelineRunStatus | StepRunStatus;

const statusStyles: Record<string, string> = {
  PENDING: "bg-gray-100 text-gray-600",
  RUNNING: "bg-yellow-100 text-yellow-800 animate-pulse",
  AWAITING_INPUT: "bg-blue-100 text-blue-800",
  COMPLETED: "bg-green-100 text-green-800",
  STEP_FAILED: "bg-red-100 text-red-800",
  FAILED: "bg-red-100 text-red-800",
  CANCELLED: "bg-gray-200 text-gray-500",
  SKIPPED: "bg-gray-100 text-gray-400",
};

interface Props {
  status: Status;
  size?: "sm" | "md";
}

export function StatusBadge({ status, size = "md" }: Props) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full font-medium",
        size === "sm" ? "px-2 py-0.5 text-xs" : "px-3 py-1 text-sm",
        statusStyles[status] ?? "bg-gray-100 text-gray-600"
      )}
    >
      {status.replace(/_/g, " ")}
    </span>
  );
}
