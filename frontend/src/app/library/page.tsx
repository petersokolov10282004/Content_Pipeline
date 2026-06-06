"use client";

import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { VideoGrid } from "@/components/library/VideoGrid";
import {
  useAssets,
  useDefaultProject,
  useDeleteAsset,
} from "@/hooks/useLibrary";

export default function LibraryPage() {
  const { data: project, isLoading: projectLoading, isError: projectError } = useDefaultProject();
  const { data: assets, isLoading: assetsLoading } = useAssets(project?.id);
  const deleteAsset = useDeleteAsset(project?.id);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  function handleDelete(assetId: string) {
    if (!confirm("Delete this asset? This cannot be undone.")) return;
    setDeletingId(assetId);
    deleteAsset.mutate(assetId, {
      onSuccess: () => toast.success("Asset deleted"),
      onError: (e) => toast.error(e.message),
      onSettled: () => setDeletingId(null),
    });
  }

  const loading = projectLoading || assetsLoading;

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

      {projectError ? (
        <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Could not reach the backend. Is the API running on :8080?
        </div>
      ) : loading ? (
        <div className="flex justify-center py-16 text-gray-400">
          <Spinner className="h-6 w-6" />
        </div>
      ) : !assets || assets.length === 0 ? (
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
      ) : (
        <VideoGrid
          assets={assets}
          projectId={project!.id}
          onDelete={handleDelete}
          deletingId={deletingId}
        />
      )}
    </div>
  );
}
