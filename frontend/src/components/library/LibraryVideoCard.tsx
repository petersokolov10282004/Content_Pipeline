"use client";

import { useState } from "react";
import { toast } from "sonner";
import { assetsApi } from "@/lib/api/assets";
import { cn } from "@/lib/utils/cn";
import { formatFileSize, formatRelative } from "@/lib/utils/formatters";
import type { Asset, AssetStatus } from "@/types/asset";

const statusStyles: Record<AssetStatus, string> = {
  READY: "bg-green-100 text-green-800",
  PENDING: "bg-yellow-100 text-yellow-800 animate-pulse",
  FAILED: "bg-red-100 text-red-800",
  DELETED: "bg-gray-200 text-gray-500",
};

interface Props {
  asset: Asset;
  projectId: string;
  onDelete: (assetId: string) => void;
  isDeleting?: boolean;
}

export function LibraryVideoCard({ asset, projectId, onDelete, isDeleting }: Props) {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);

  const isVideo = asset.assetType === "GAMEPLAY_VIDEO" || asset.contentType.startsWith("video/");
  const canPreview = asset.status === "READY" && isVideo;

  async function loadPreview() {
    setLoadingPreview(true);
    try {
      const { downloadUrl } = await assetsApi.downloadUrl(projectId, asset.id);
      setPreviewUrl(downloadUrl);
    } catch {
      toast.error("Could not load preview");
    } finally {
      setLoadingPreview(false);
    }
  }

  return (
    <div className="group flex flex-col overflow-hidden rounded-lg border bg-white shadow-sm">
      <div className="relative aspect-[9/16] w-full bg-gray-900">
        {previewUrl ? (
          // eslint-disable-next-line jsx-a11y/media-has-caption
          <video src={previewUrl} controls autoPlay className="h-full w-full object-contain" />
        ) : (
          <button
            type="button"
            disabled={!canPreview || loadingPreview}
            onClick={loadPreview}
            className="flex h-full w-full items-center justify-center text-gray-400 disabled:cursor-not-allowed"
          >
            {canPreview ? (
              <span className="flex flex-col items-center gap-2">
                <svg className="h-12 w-12" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M8 5v14l11-7z" />
                </svg>
                <span className="text-xs">{loadingPreview ? "Loading…" : "Preview"}</span>
              </span>
            ) : (
              <span className="text-xs uppercase tracking-wide">
                {asset.status === "PENDING" ? "Processing" : asset.assetType.replace(/_/g, " ")}
              </span>
            )}
          </button>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-2 p-3">
        <div className="flex items-start justify-between gap-2">
          <h3 className="truncate text-sm font-semibold text-gray-900" title={asset.name}>
            {asset.name}
          </h3>
          <span
            className={cn(
              "shrink-0 rounded-full px-2 py-0.5 text-xs font-medium",
              statusStyles[asset.status]
            )}
          >
            {asset.status}
          </span>
        </div>

        <div className="mt-auto flex items-center justify-between text-xs text-gray-500">
          <span>{asset.sizeBytes != null ? formatFileSize(asset.sizeBytes) : "—"}</span>
          <span>{formatRelative(asset.createdAt)}</span>
        </div>

        <button
          type="button"
          onClick={() => onDelete(asset.id)}
          disabled={isDeleting}
          className="mt-1 self-start text-xs font-medium text-red-600 hover:text-red-700 disabled:opacity-50"
        >
          {isDeleting ? "Deleting…" : "Delete"}
        </button>
      </div>
    </div>
  );
}
