"use client";

import { LibraryVideoCard } from "./LibraryVideoCard";
import type { Asset } from "@/types/asset";

interface Props {
  assets: Asset[];
  projectId: string;
  onDelete: (assetId: string) => void;
  deletingId?: string | null;
}

export function VideoGrid({ assets, projectId, onDelete, deletingId }: Props) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
      {assets.map((asset) => (
        <LibraryVideoCard
          key={asset.id}
          asset={asset}
          projectId={projectId}
          onDelete={onDelete}
          isDeleting={deletingId === asset.id}
        />
      ))}
    </div>
  );
}
