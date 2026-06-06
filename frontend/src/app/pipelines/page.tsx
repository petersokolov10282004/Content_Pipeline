import Link from "next/link";
import { EmptyState } from "@/components/ui/EmptyState";

export default function PipelinesPage() {
  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Pipelines</h1>
          <p className="mt-1 text-sm text-gray-500">All pipeline runs</p>
        </div>
        <Link
          href="/pipelines/new"
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          New Pipeline
        </Link>
      </div>
      {/* PipelineRunList goes here — implemented in Phase 3 */}
      <EmptyState
        title="No pipeline runs yet"
        description="Create your first pipeline to get started."
        action={
          <Link
            href="/pipelines/new"
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white"
          >
            New Pipeline
          </Link>
        }
      />
    </div>
  );
}
