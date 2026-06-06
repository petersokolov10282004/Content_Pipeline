export type AssetType =
  | "GAMEPLAY_VIDEO"
  | "BACKGROUND_MUSIC"
  | "FONT"
  | "IMAGE"
  | "OTHER";

export type AssetStatus = "PENDING" | "READY" | "FAILED" | "DELETED";

export interface Asset {
  id: string;
  projectId: string;
  name: string;
  assetType: AssetType;
  contentType: string;
  originalFilename: string;
  sizeBytes: number | null;
  status: AssetStatus;
  createdAt: string;
  updatedAt: string;
}

/** Step 1 request: declare the file before uploading. */
export interface CreateUploadUrlRequest {
  name: string;
  assetType: AssetType;
  contentType: string;
  originalFilename: string;
  sizeBytes?: number;
}

/** Step 1 response: presigned PUT target + the asset to confirm later. */
export interface UploadUrlResponse {
  assetId: string;
  uploadUrl: string;
  storageKey: string;
  expiresAt: string;
}

/** Step 3 request: confirm the upload landed in storage. */
export interface ConfirmUploadRequest {
  sizeBytes?: number;
}

export interface DownloadUrlResponse {
  downloadUrl: string;
  expiresAt: string;
}
