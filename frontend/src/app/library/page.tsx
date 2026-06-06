import Link from "next/link";
import { EmptyState } from "@/components/ui/EmptyState";

export default function LibraryPage() {
  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Gameplay Library</h1>
          <p className="mt-1 text-sm text-gray-500">Manage your gameplay video clips</p>
        </div>
        <Link
          href="/library/upload"
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          Upload Video
        </Link>
      </div>
      {/* VideoGrid — implemented in Phase 2 */}
      <EmptyState
        title="No videos yet"
        description="Upload your first gameplay clip to get started."
        action={
          <Link
            href="/library/upload"
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white"
          >
            Upload Video
          </Link>
        }
      />
    </div>
  );
}
