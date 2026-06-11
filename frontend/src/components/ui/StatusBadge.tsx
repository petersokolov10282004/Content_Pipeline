import { cn } from "@/lib/utils/cn";
import type { PipelineRunStatus, StepRunStatus } from "@/types/pipeline";

type Status = PipelineRunStatus | StepRunStatus;

const config: Record<string, { dot: string; bg: string; text: string; label: string }> = {
  PENDING:        { dot: "bg-slate-400",   bg: "bg-slate-100",   text: "text-slate-600",  label: "Pending"   },
  RUNNING:        { dot: "bg-blue-500",    bg: "bg-blue-50",     text: "text-blue-700",   label: "Running"   },
  AWAITING_INPUT: { dot: "bg-amber-500",   bg: "bg-amber-50",    text: "text-amber-700",  label: "Waiting"   },
  COMPLETED:      { dot: "bg-emerald-500", bg: "bg-emerald-50",  text: "text-emerald-700",label: "Completed" },
  STEP_FAILED:    { dot: "bg-red-500",     bg: "bg-red-50",      text: "text-red-700",    label: "Failed"    },
  FAILED:         { dot: "bg-red-500",     bg: "bg-red-50",      text: "text-red-700",    label: "Failed"    },
  CANCELLED:      { dot: "bg-slate-400",   bg: "bg-slate-100",   text: "text-slate-500",  label: "Cancelled" },
  SKIPPED:        { dot: "bg-slate-300",   bg: "bg-slate-100",   text: "text-slate-400",  label: "Skipped"   },
};

interface Props { status: Status; size?: "sm" | "md" }

export function StatusBadge({ status, size = "md" }: Props) {
  const rawLabel = String(status).replace(/_/g, " ");
  const c = config[status] ?? { dot: "bg-slate-400", bg: "bg-slate-100", text: "text-slate-600", label: rawLabel };
  const isRunning = status === "RUNNING";

  return (
    <span className={cn(
      "inline-flex items-center gap-1.5 rounded-full font-medium",
      size === "sm" ? "px-2 py-0.5 text-xs" : "px-2.5 py-1 text-xs",
      c.bg, c.text
    )}>
      <span className={cn("rounded-full shrink-0 w-1.5 h-1.5", c.dot, isRunning && "animate-pulse")} />
      {c.label}
    </span>
  );
}
