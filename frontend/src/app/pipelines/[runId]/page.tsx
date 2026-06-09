"use client";

import Link from "next/link";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatRelative } from "@/lib/utils/formatters";
import { useDefaultProjectForPipeline, usePipelineRun } from "@/hooks/usePipelines";
import { usePipelineEvents } from "@/hooks/usePipelineEvents";
import type { StepRunStatus } from "@/types/pipeline";

const stepStatusIcon: Record<StepRunStatus, string> = {
  PENDING: "○",
  RUNNING: "◉",
  COMPLETED: "✓",
  FAILED: "✗",
  AWAITING_INPUT: "◎",
  SKIPPED: "–",
};

const stepStatusColor: Record<StepRunStatus, string> = {
  PENDING: "text-gray-400",
  RUNNING: "text-yellow-600 animate-pulse",
  COMPLETED: "text-green-600",
  FAILED: "text-red-600",
  AWAITING_INPUT: "text-blue-600",
  SKIPPED: "text-gray-300",
};

export default function PipelineRunPage({ params }: { params: { runId: string } }) {
  const { data: project } = useDefaultProjectForPipeline();
  const { data: run, isLoading } = usePipelineRun(project?.id, params.runId);
  usePipelineEvents(project?.id, params.runId, run?.status);

  if (isLoading || !project) {
    return (
      <div className="flex justify-center py-16 text-gray-400">
        <Spinner className="h-6 w-6" />
      </div>
    );
  }

  if (!run) {
    return (
      <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Run not found.{" "}
        <Link href="/pipelines" className="underline">Back to runs</Link>
      </div>
    );
  }

  return (
    <div className="max-w-2xl">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <Link href="/pipelines" className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
            ← Pipeline Runs
          </Link>
          <h1 className="mt-1 text-2xl font-bold text-gray-900">
            {run.id.slice(0, 8)}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{formatRelative(run.createdAt)}</p>
        </div>
        <StatusBadge status={run.status} />
      </div>

      {/* Step progress tracker */}
      <div className="rounded-lg border bg-white p-6">
        <h2 className="mb-4 text-sm font-semibold text-gray-700">Steps</h2>
        <ol className="space-y-4">
          {run.steps?.map((step) => {
            const label = step.stepName ?? step.stepHandlerKey?.replace(/_/g, " ") ?? "Step";
            return (
              <li key={step.id} className="flex items-start gap-3">
                <span className={`mt-0.5 font-mono text-lg leading-none ${stepStatusColor[step.status]}`}>
                  {stepStatusIcon[step.status]}
                </span>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-gray-900">
                      {label}
                    </span>
                    <StatusBadge status={step.status} size="sm" />
                  </div>
                  {step.errorMessage && (
                    <p className="mt-1 text-xs text-red-600">{step.errorMessage}</p>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      </div>

      <p className="mt-4 text-center text-xs text-gray-400">
        Live updates via SSE
      </p>
    </div>
  );
}
