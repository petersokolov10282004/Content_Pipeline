export type ArtifactType =
  | "SCRIPT"
  | "SUBTITLE"
  | "RENDER_CONFIG"
  | "RENDERED_VIDEO"
  | "PUBLISH_CONFIG"
  | "PUBLISH_RESULT";

interface ArtifactBase {
  id: string;
  type: ArtifactType;
  version: number;
  runId: string;
  createdAt: string;
}

export interface ScriptArtifact extends ArtifactBase {
  type: "SCRIPT";
  content: string;
  title: string | null;
  genre: string | null;
  wordCount: number;
  estimatedDurationSeconds: number;
}

export interface SubtitleArtifact extends ArtifactBase {
  type: "SUBTITLE";
  srtContent: string;
  cueCount: number;
  totalWords: number;
}

export interface RenderedVideoArtifact extends ArtifactBase {
  type: "RENDERED_VIDEO";
  streamUrl: string;
  downloadUrl: string;
  widthPx: number;
  heightPx: number;
  durationSeconds: number;
  fileSizeBytes: number;
}

export interface PublishResultArtifact extends ArtifactBase {
  type: "PUBLISH_RESULT";
  platform: "YOUTUBE";
  publishStatus: "PUBLISHED" | "FAILED" | "MOCK";
  videoId: string | null;
  videoUrl: string | null;
  title: string;
  publishedAt: string | null;
}

export type ArtifactResponse =
  | ScriptArtifact
  | SubtitleArtifact
  | RenderedVideoArtifact
  | PublishResultArtifact;

export interface ArtifactSummary {
  id: string;
  type: ArtifactType;
  step: string;
  version: number;
  createdAt: string;
}
