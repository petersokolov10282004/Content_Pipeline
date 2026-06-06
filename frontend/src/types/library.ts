export interface LibraryVideo {
  id: string;
  title: string;
  game: string | null;
  tags: string[];
  durationSeconds: number;
  fileSizeBytes: number;
  thumbnailUrl: string | null;
  streamUrl: string;
  status: "PROCESSING" | "READY" | "FAILED";
  uploadedAt: string;
}

export interface LibraryVideoListResponse {
  content: LibraryVideo[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
