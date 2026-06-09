"use client";

import Link from "next/link";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatRelative } from "@/lib/utils/formatters";
import { useDefaultProjectForPipeline, usePipelineRuns } from "@/hooks/usePipelines";

export default function PipelinesPage() {
  const { data: project, isLoading: projectLoading } = useDefaultProjectForPipeline();
  const { data, isLoading: runsLoading } = usePipelineRuns(project?.id);

  const loading = projectLoading || runsLoading;
  const runs = data?.content ?? [];

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Pipeline Runs</h1>
          <p className="mt-1 text-sm text-gray-500">All your content generation runs</p>
        </div>
        <Link
          href="/pipelines/new"
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          New Run
        </Link>
      </div>

      {loading ? (
        <div className="flex justify-center py-16 text-gray-400">
          <Spinner className="h-6 w-6" />
        </div>
      ) : runs.length === 0 ? (
        <EmptyState
          title="No runs yet"
          description="Start your first pipeline run to generate a video."
          action={
            <Link
              href="/pipelines/new"
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white"
            >
              New Run
            </Link>
          }
        />
      ) : (
        <div className="overflow-hidden rounded-lg border bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Run</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Status</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Started</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white">
              {runs.map((run) => (
                <tr key={run.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <Link
                      href={`/pipelines/${run.id}`}
                      className="text-sm font-medium text-indigo-600 hover:text-indigo-500"
                    >
                      {run.templateName ?? run.id.slice(0, 8)}
                    </Link>
                  </td>
                  <td className="px-6 py-4">
                    <StatusBadge status={run.status} size="sm" />
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-500">
                    {run.startedAt ? formatRelative(run.startedAt) : "—"}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-500">
                    {formatRelative(run.createdAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
