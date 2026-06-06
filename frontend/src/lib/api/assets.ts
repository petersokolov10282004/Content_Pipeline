import { api } from "./client";
import type {
  Asset,
  ConfirmUploadRequest,
  CreateUploadUrlRequest,
  DownloadUrlResponse,
  UploadUrlResponse,
} from "@/types/asset";

const base = (projectId: string) => `/api/v1/projects/${projectId}/assets`;

export const assetsApi = {
  list: (projectId: string) => api.get<Asset[]>(base(projectId)),

  get: (projectId: string, assetId: string) =>
    api.get<Asset>(`${base(projectId)}/${assetId}`),

  createUploadUrl: (projectId: string, body: CreateUploadUrlRequest) =>
    api.post<UploadUrlResponse>(`${base(projectId)}/upload-url`, body),

  confirmUpload: (projectId: string, assetId: string, body: ConfirmUploadRequest) =>
    api.post<Asset>(`${base(projectId)}/${assetId}/confirm-upload`, body),

  downloadUrl: (projectId: string, assetId: string) =>
    api.get<DownloadUrlResponse>(`${base(projectId)}/${assetId}/download-url`),

  remove: (projectId: string, assetId: string) =>
    api.delete<void>(`${base(projectId)}/${assetId}`),
};

/**
 * PUT raw bytes directly to R2 via the presigned URL. This bypasses the API
 * client entirely: no auth header, and the Content-Type MUST match what was
 * signed in createUploadUrl, or R2 rejects the signature.
 */
export async function putToPresignedUrl(
  uploadUrl: string,
  file: File,
  contentType: string,
  onProgress?: (fraction: number) => void
): Promise<void> {
  // Use XMLHttpRequest (not fetch) so we get upload progress events.
  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", uploadUrl, true);
    xhr.setRequestHeader("Content-Type", contentType);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(e.loaded / e.total);
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`Upload failed: ${xhr.status} ${xhr.statusText}`));
      }
    };
    xhr.onerror = () => reject(new Error("Upload failed: network error"));
    xhr.send(file);
  });
}

/** Map a browser MIME type to the backend AssetType for the gameplay library. */
export function inferAssetType(file: File): CreateUploadUrlRequest["assetType"] {
  if (file.type.startsWith("video/")) return "GAMEPLAY_VIDEO";
  if (file.type.startsWith("audio/")) return "BACKGROUND_MUSIC";
  if (file.type.startsWith("image/")) return "IMAGE";
  return "OTHER";
}
