"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useState } from "react";
import {
  assetsApi,
  inferAssetType,
  putToPresignedUrl,
} from "@/lib/api/assets";
import { ensureDefaultProject } from "@/lib/api/projects";
import type { Asset } from "@/types/asset";
import type { Project } from "@/types/project";

export const libraryKeys = {
  defaultProject: ["library", "default-project"] as const,
  assets: (projectId: string) => ["library", "assets", projectId] as const,
};

/** Resolves (creating if needed) the project that backs the library. */
export function useDefaultProject() {
  return useQuery<Project>({
    queryKey: libraryKeys.defaultProject,
    queryFn: ensureDefaultProject,
    staleTime: 5 * 60_000,
  });
}

/** Lists READY + PENDING assets for a project; pass undefined to disable. */
export function useAssets(projectId: string | undefined) {
  return useQuery<Asset[]>({
    queryKey: libraryKeys.assets(projectId ?? "none"),
    queryFn: () => assetsApi.list(projectId!),
    enabled: !!projectId,
  });
}

export type UploadProgress = {
  fileName: string;
  fraction: number;
  phase: "requesting" | "uploading" | "confirming" | "done" | "error";
};

/**
 * Orchestrates the three-step upload: request presigned URL → PUT to R2 →
 * confirm. Invalidates the asset list on success. Exposes per-file progress.
 */
export function useUploadAsset(projectId: string | undefined) {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState<UploadProgress | null>(null);

  const mutation = useMutation<Asset, Error, File>({
    mutationFn: async (file: File) => {
      if (!projectId) {
        throw new Error("No project available for upload");
      }
      const contentType = file.type || "application/octet-stream";

      setProgress({ fileName: file.name, fraction: 0, phase: "requesting" });
      const { assetId, uploadUrl } = await assetsApi.createUploadUrl(projectId, {
        name: file.name,
        assetType: inferAssetType(file),
        contentType,
        originalFilename: file.name,
        sizeBytes: file.size,
      });

      setProgress({ fileName: file.name, fraction: 0, phase: "uploading" });
      await putToPresignedUrl(uploadUrl, file, contentType, (fraction) =>
        setProgress({ fileName: file.name, fraction, phase: "uploading" })
      );

      setProgress({ fileName: file.name, fraction: 1, phase: "confirming" });
      const asset = await assetsApi.confirmUpload(projectId, assetId, {
        sizeBytes: file.size,
      });

      setProgress({ fileName: file.name, fraction: 1, phase: "done" });
      return asset;
    },
    onSuccess: () => {
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: libraryKeys.assets(projectId) });
        queryClient.invalidateQueries({ queryKey: libraryKeys.defaultProject });
      }
    },
    onError: (_err, file) => {
      setProgress({ fileName: file.name, fraction: 0, phase: "error" });
    },
  });

  return { ...mutation, progress, resetProgress: () => setProgress(null) };
}

export function useDeleteAsset(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (assetId: string) => {
      if (!projectId) throw new Error("No project available");
      return assetsApi.remove(projectId, assetId);
    },
    onSuccess: () => {
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: libraryKeys.assets(projectId) });
        queryClient.invalidateQueries({ queryKey: libraryKeys.defaultProject });
      }
    },
  });
}
