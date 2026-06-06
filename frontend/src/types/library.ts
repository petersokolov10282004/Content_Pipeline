// The gameplay library is backed by project Assets. These re-exports keep a
// stable "library" import path while the underlying model lives in asset.ts.
export type {
  Asset,
  AssetType,
  AssetStatus,
  CreateUploadUrlRequest,
  UploadUrlResponse,
  ConfirmUploadRequest,
  DownloadUrlResponse,
} from "./asset";
