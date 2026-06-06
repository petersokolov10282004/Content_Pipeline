export type PipelineRunStatus =
  | "PENDING"
  | "RUNNING"
  | "AWAITING_INPUT"
  | "STEP_FAILED"
  | "COMPLETED"
  | "CANCELLED";

export type StepRunStatus =
  | "PENDING"
  | "RUNNING"
  | "AWAITING_INPUT"
  | "COMPLETED"
  | "FAILED"
  | "SKIPPED";

export type PipelineStepName =
  | "GENERATE_STORY"
  | "GENERATE_SUBTITLES"
  | "RENDER_VIDEO"
  | "UPLOAD_VIDEO";

export type UserInputType = "GAMEPLAY_SELECTION" | "SCRIPT_APPROVAL";

export type PipelineRunEvent =
  | { type: "STEP_STARTED"; step: PipelineStepName; timestamp: string }
  | { type: "STEP_COMPLETED"; step: PipelineStepName; artifactId: string; timestamp: string }
  | { type: "STEP_FAILED"; step: PipelineStepName; error: string; timestamp: string }
  | { type: "AWAITING_INPUT"; step: PipelineStepName; inputType: UserInputType; timestamp: string }
  | { type: "RUN_COMPLETED"; timestamp: string }
  | { type: "RUN_FAILED"; error: string; timestamp: string };

export interface PipelineStepState {
  step: PipelineStepName;
  status: StepRunStatus;
  startedAt: string | null;
  completedAt: string | null;
  artifactId: string | null;
  errorMessage: string | null;
}

export interface PipelineRunSummary {
  id: string;
  title: string | null;
  status: PipelineRunStatus;
  currentStep: PipelineStepName | null;
  createdAt: string;
  updatedAt: string;
}

export interface PipelineRunDetail extends PipelineRunSummary {
  prompt: string;
  steps: PipelineStepState[];
}

export interface PipelineRunListResponse {
  content: PipelineRunSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreatePipelineRunRequest {
  pipelineTemplateId: string;
  inputAssetIds: { gameplay?: string; [key: string]: string | undefined };
  inputParams: {
    prompt: string;
    genre?: string;
    tone?: string;
    publishConfigId?: string;
  };
  title?: string;
}
