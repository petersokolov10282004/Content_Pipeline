"use client";

import Link from "next/link";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { MonoLabel } from "@/components/ui/MonoLabel";
import { formatRelative } from "@/lib/utils/formatters";
import { useDefaultProjectForPipeline, usePipelineRuns } from "@/hooks/usePipelines";

export default function PipelinesPage() {
  const { data: project, isLoading: projectLoading } = useDefaultProjectForPipeline();
  const { data, isLoading: runsLoading } = usePipelineRuns(project?.id);

  const loading = projectLoading || runsLoading;
  const runs = data?.content ?? [];

  return (
    <div>
      <div className="mb-7 flex items-end justify-between">
        <div>
          <MonoLabel className="text-slate-400 block mb-1">History</MonoLabel>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Pipeline Runs</h1>
        </div>
        <Link
          href="/pipelines/new"
          className="inline-flex items-center gap-1.5 rounded-lg bg-accent hover:bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-colors"
        >
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M6.5 2v9M2 6.5h9" stroke="white" strokeWidth="1.75" strokeLinecap="round"/>
          </svg>
          New run
        </Link>
      </div>

      {loading ? (
        <div className="flex justify-center py-20"><Spinner className="h-5 w-5 text-slate-400" /></div>
      ) : runs.length === 0 ? (
        <EmptyState
          title="No runs yet"
          description="Start your first pipeline run to generate a video."
          action={
            <Link href="/pipelines/new" className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-blue-600 transition-colors">
              New run
            </Link>
          }
        />
      ) : (
        <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
          <table className="min-w-full">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/60">
                {["Run ID", "Status", "Started", "Created"].map((h) => (
                  <th key={h} className="px-5 py-3 text-left">
                    <MonoLabel className="text-slate-400">{h}</MonoLabel>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {runs.map((run) => (
                <tr key={run.id} className="hover:bg-slate-50/50 transition-colors">
                  <td className="px-5 py-3.5">
                    <Link href={`/pipelines/${run.id}`} className="font-mono text-xs bg-slate-100 hover:bg-blue-50 hover:text-accent px-2 py-1 rounded transition-colors text-slate-700">
                      {run.id.slice(0, 12)}
                    </Link>
                  </td>
                  <td className="px-5 py-3.5"><StatusBadge status={run.status} size="sm" /></td>
                  <td className="px-5 py-3.5"><MonoLabel className="text-slate-500">{run.startedAt ? formatRelative(run.startedAt) : "—"}</MonoLabel></td>
                  <td className="px-5 py-3.5"><MonoLabel className="text-slate-500">{formatRelative(run.createdAt)}</MonoLabel></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
