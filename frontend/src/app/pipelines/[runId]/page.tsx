"use client";

import Link from "next/link";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { MonoLabel } from "@/components/ui/MonoLabel";
import { formatRelative } from "@/lib/utils/formatters";
import { useDefaultProjectForPipeline, usePipelineRun } from "@/hooks/usePipelines";
import { usePipelineEvents } from "@/hooks/usePipelineEvents";
import type { StepRunStatus } from "@/types/pipeline";
import { cn } from "@/lib/utils/cn";

const dotStyle: Record<StepRunStatus, string> = {
  PENDING:        "bg-slate-200 ring-slate-50",
  RUNNING:        "bg-accent ring-blue-100 animate-pulse",
  COMPLETED:      "bg-emerald-500 ring-emerald-50",
  FAILED:         "bg-red-500 ring-red-50",
  AWAITING_INPUT: "bg-amber-500 ring-amber-50",
  SKIPPED:        "bg-slate-200 ring-slate-50",
};

const labelStyle: Record<StepRunStatus, string> = {
  PENDING:        "text-slate-400",
  RUNNING:        "text-accent",
  COMPLETED:      "text-emerald-600",
  FAILED:         "text-red-600",
  AWAITING_INPUT: "text-amber-600",
  SKIPPED:        "text-slate-400",
};

const stepLabels: Record<string, string> = {
  GENERATE_STORY:     "Generate story script",
  GENERATE_SUBTITLES: "Generate subtitles",
  RENDER_VIDEO:       "Render video",
  UPLOAD_VIDEO:       "Upload to YouTube",
};

export default function PipelineRunPage({ params }: { params: { runId: string } }) {
  const { data: project } = useDefaultProjectForPipeline();
  const { data: run, isLoading } = usePipelineRun(project?.id, params.runId);
  usePipelineEvents(project?.id, params.runId, run?.status);

  if (isLoading || !project) {
    return <div className="flex justify-center py-20"><Spinner className="h-5 w-5 text-slate-400" /></div>;
  }

  if (!run) {
    return (
      <div className="max-w-xl rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">
        Run not found. <Link href="/pipelines" className="underline font-medium">Back to runs</Link>
      </div>
    );
  }

  const steps = run.steps ?? [];
  const completedCount = steps.filter((s) => s.status === "COMPLETED").length;
  const progressPct = steps.length ? Math.round((completedCount / steps.length) * 100) : 0;

  return (
    <div className="max-w-xl">
      {/* ── Dark header readout ─────────────────────────────────────────── */}
      <div className="relative rounded-xl overflow-hidden bg-ink-950 grid-overlay mb-7 px-7 py-8">
        <span className="absolute top-3 left-3 w-2 h-2 border-t border-l border-white/20" />
        <span className="absolute bottom-3 right-3 w-2 h-2 border-b border-r border-white/20" />

        <Link href="/pipelines" className="inline-flex items-center gap-1 text-ink-400 hover:text-ink-200 transition-colors mb-4 text-xs">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M8 2L4 6l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          Pipeline runs
        </Link>

        <div className="flex items-start justify-between gap-4">
          <div>
            <MonoLabel className="text-ink-400 block mb-1">{formatRelative(run.createdAt)}</MonoLabel>
            <h1 className="text-2xl font-bold text-white tracking-tight font-mono">{run.id.slice(0, 12)}</h1>
          </div>
          <StatusBadge status={run.status} />
        </div>

        {/* Progress bar */}
        <div className="mt-6">
          <div className="flex items-center justify-between mb-1.5">
            <MonoLabel className="text-ink-400">Progress</MonoLabel>
            <MonoLabel className="text-ink-300">{progressPct}%</MonoLabel>
          </div>
          <div className="h-px bg-white/10 rounded-full overflow-hidden">
            <div
              className="h-full bg-accent transition-all duration-700 ease-out rounded-full"
              style={{ width: `${progressPct}%` }}
            />
          </div>
        </div>
      </div>

      {/* ── Step timeline ───────────────────────────────────────────────── */}
      <div className="rounded-xl border border-slate-200 bg-white shadow-sm p-6">
        <MonoLabel className="text-slate-400 block mb-5">Step log</MonoLabel>
        <ol className="relative space-y-0">
          {steps.map((step, idx) => {
            const isLast = idx === steps.length - 1;
            const label = stepLabels[step.stepHandlerKey] ?? step.stepName;
            const statusText = step.status.charAt(0) + step.status.slice(1).toLowerCase();

            return (
              <li key={step.id} className="flex gap-4 relative">
                {/* connector */}
                {!isLast && (
                  <div className="absolute left-[11px] top-6 bottom-0 w-px bg-slate-100" />
                )}
                {/* dot */}
                <div className="relative z-10 mt-1 shrink-0">
                  <div className={cn("w-5 h-5 rounded-full ring-4 flex items-center justify-center", dotStyle[step.status])}>
                    {step.status === "COMPLETED" && (
                      <svg width="9" height="9" viewBox="0 0 9 9" fill="none">
                        <path d="M1.5 4.5l2 2L7.5 2" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    )}
                    {step.status === "FAILED" && (
                      <svg width="8" height="8" viewBox="0 0 8 8" fill="none">
                        <path d="M2 2l4 4M6 2L2 6" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
                      </svg>
                    )}
                  </div>
                </div>
                {/* content */}
                <div className={cn("flex-1", isLast ? "pb-0" : "pb-6")}>
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-slate-900">{label}</span>
                    <span className={cn("text-xs font-medium", labelStyle[step.status])}>{statusText}</span>
                  </div>
                  {step.errorMessage && (
                    <p className="mt-1.5 text-xs text-red-600 bg-red-50 rounded px-2 py-1">{step.errorMessage}</p>
                  )}
                  {step.startedAt && step.status !== "PENDING" && (
                    <MonoLabel className="text-slate-400 mt-1 block">{formatRelative(step.startedAt)}</MonoLabel>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      </div>

      <div className="mt-4 text-center">
        <MonoLabel className="text-slate-400">Live · SSE</MonoLabel>
      </div>
    </div>
  );
}
