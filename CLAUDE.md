# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

ContentPipeline is a **generic content automation pipeline platform**. It is not a single-purpose video tool — new pipeline types are added by implementing `PipelineStepHandler` + a Temporal Workflow without touching existing code. The first pipeline implemented is a short-form story gameplay video: prompt → AI script → AI subtitles → FFmpeg render → YouTube upload.

Monorepo layout:
- `backend/` — Spring Boot 3.3 / Java 21 / Maven
- `frontend/` — Next.js 14 App Router / TypeScript / TailwindCSS
- `docker-compose.yml` — PostgreSQL 16 + Temporal server + UI

## Development Commands

### Infrastructure (required before running backend)
```bash
docker-compose up -d                  # start PostgreSQL + Temporal
docker-compose --profile redis up -d  # also start Redis (optional, Phase 3+)
```
Temporal UI is at http://localhost:8088.

### Backend
```bash
cd backend
./mvnw spring-boot:run                          # run with dev defaults
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod  # prod profile
./mvnw clean package                            # build JAR
./mvnw test                                     # all tests
./mvnw test -Dtest=SomeTestClass                # single test class
./mvnw test -Dtest=SomeTestClass#methodName     # single test method
./mvnw flyway:info                              # check migration status
```

### Frontend
```bash
cd frontend
npm install          # first-time setup
npm run dev          # dev server on :3000
npm run build        # production build
npm run type-check   # tsc --noEmit (no compilation output)
npm run lint         # eslint
```

### Environment
Copy `.env.example` to `.env` and fill in credentials before running. Required for backend: `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_ENDPOINT`, `ANTHROPIC_API_KEY`. Frontend reads `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`).

## Architecture

### The Pipeline Abstraction

The entire platform is built around one interface:

```java
// backend/src/main/java/com/contentpipeline/pipeline/handler/PipelineStepHandler.java
public interface PipelineStepHandler {
    String handlerKey();
    StepResult execute(StepContext context) throws StepExecutionException;
}
```

- `handlerKey()` returns a globally unique string stored in the `pipeline_step_definitions.step_handler_key` DB column.
- `StepHandlerRegistry` auto-discovers all `@Component` beans implementing the interface — no manual registration.
- `StepContext` carries all inputs: `inputArtifactIds` (named artifact UUID refs), `inputAssetIds` (named asset UUID refs), `stepConfig` (from `PipelineStepDefinition.configJson`), and `devUserId` (auth placeholder).
- `StepResult` carries `outputArtifactIds` (named artifact UUID refs).

**Adding a new pipeline type:**
1. Write `*StepHandler` classes in `steps/<pipeline-name>/`, annotate `@Component`
2. Write `*WorkflowImpl.java` in `workflow/` implementing the Temporal workflow
3. Seed a `PipelineTemplate` + `PipelineStepDefinition` rows via `DataInitializer`

No changes to any existing class.

### Workflow Engine: Temporal

Pipelines run as Temporal workflows (`workflow/StoryPipelineWorkflowImpl.java`). Each step is a Temporal Activity. This is intentional — FFmpeg renders take minutes, YouTube uploads are large transfers. Temporal provides crash recovery, retry with backoff, and per-step visibility via its UI.

- Workers run in the same JVM as the API server (Phase 1). They can be extracted to a separate process later.
- Long-running steps (render, upload) use Temporal heartbeats to indicate liveness.
- The `RenderVideoStepHandler` does **not** run FFmpeg inline — it creates a `RenderJob` record and returns. The Temporal Activity (`RenderVideoActivity`) polls for completion while the `FfmpegRenderWorker` (`@Scheduled`) claims and processes the job.

### Artifact Polymorphism

All artifact types share a single `artifacts` table (`SINGLE_TABLE` JPA inheritance, `artifact_type` discriminator column). Subtypes: `SCRIPT`, `SUBTITLE`, `RENDER_CONFIG`, `RENDERED_VIDEO`, `PUBLISH_CONFIG`, `PUBLISH_RESULT`. Each subtype's columns are sparse (null for other types). Adding a new artifact type = new `@Entity @DiscriminatorValue("X") class XArtifact extends Artifact`.

### Media Storage: Cloudflare R2

Assets and rendered videos are stored in R2 (S3-compatible). The PostgreSQL entity only stores the `storageKey` + `storageBucket`. Files are never served through the API — all access uses presigned URLs from `StorageService.generatePresignedDownloadUrl()`. Client uploads go directly to R2 via presigned PUT URLs; the backend confirms with a `confirm-upload` endpoint.

R2 key conventions:
- Assets: `assets/{projectId}/{assetId}/{filename}`
- Renders: `renders/{projectId}/{runId}/{stepRunId}/{uuid}.mp4`

### Real-time Updates: SSE

`SseEmitterRegistry` (`common/sse/`) maintains one `SseEmitter` per active pipeline run. Temporal workflow steps call `sseEmitterRegistry.emit(runId, event)` on state transitions. The frontend `PipelineEventSource` class (`lib/sse/pipelineEventSource.ts`) subscribes with exponential backoff reconnect and closes automatically on `RUN_COMPLETED`/`RUN_FAILED`.

The `usePipelineEvents` hook (Phase 4) invalidates TanStack Query's cache on each event, so the rest of the UI re-renders without needing to manage separate state.

### Auth Seam (Deferred)

Auth is intentionally absent. The seam is in three places:
- **Backend**: `SecurityConfig` is fully permissive. All controllers accept an `X-Dev-User-Id` header. When JWT is added: add `spring-boot-starter-oauth2-resource-server` and update `SecurityConfig` — no controller changes needed.
- **Frontend**: Every page/hook calls `useSession()` from `lib/auth/authProvider.tsx` — never from NextAuth directly. The provider today returns `DEV_USER`; swap its value for NextAuth session when ready.
- **Frontend**: `middleware.ts` exists as a passthrough stub. Add session check there for route protection.

### Backend Package Structure

Feature-package (vertical slice) layout under `com.contentpipeline`:

| Package | Responsibility |
|---|---|
| `common/` | `BaseEntity`, `StorageService`, `SseEmitterRegistry`, exceptions |
| `config/` | Spring beans for Temporal, R2, Anthropic, Security, CORS |
| `project/` | Project CRUD |
| `asset/` | Asset upload/download lifecycle |
| `artifact/` | Artifact persistence and polymorphic retrieval |
| `pipeline/template/` | `PipelineTemplate` + `PipelineStepDefinition` (read-only from API, seeded by `DataInitializer`) |
| `pipeline/run/` | `PipelineRun` + `PipelineStepRun` execution state |
| `pipeline/handler/` | `PipelineStepHandler` interface, `StepContext`, `StepResult` |
| `steps/story/` | First pipeline's four step handlers |
| `steps/registry/` | `StepHandlerRegistry` |
| `render/` | `RenderJob` entity, `FfmpegRenderWorker`, `FfmpegCommandBuilder` |
| `upload/` | `UploadJob` entity, `YouTubeUploadActivity` (mock + real) |
| `social/` | `SocialAccount` placeholder for OAuth |
| `workflow/` | Temporal workflow interfaces and implementations |

### Database

Flyway migrations in `backend/src/main/resources/db/migration/`. Current: `V1__init_schema.sql` (all tables). JPA `ddl-auto` is `validate` — the schema must exist before the app starts. Never set `ddl-auto=update` in this project; always write a new `V{n}__description.sql` migration instead.

### Frontend Data Flow

```
TanStack Query (cache + fetching) ← invalidated by → usePipelineEvents (SSE hook)
         ↓                                                       ↑
   lib/api/client.ts                               lib/sse/pipelineEventSource.ts
         ↓
   /api/v1/* (backend REST)
```

Server state lives entirely in TanStack Query. React Context is only used for the auth session (`AuthProvider`) and toast notifications.

### Mock vs Production Profiles

`MockYouTubeUploadActivityImpl` is active when `SPRING_PROFILES_ACTIVE` is not `prod` (annotated `@Profile("!prod")`). It generates a fake video ID and sets `PublishResultArtifact.publishStatus = MOCK`. The real `YouTubeUploadActivityImpl` is `@Profile("prod")`. Switch profiles, not code.

## FFmpeg Render Output Spec

1080×1920 MP4, H.264 (`libx264`, CRF 23, `fast` preset), AAC 128k, `+faststart`, subtitles burned in via `subtitles=` filter. FFmpeg binary path configured by `render.ffmpeg.path` (env: `RENDER_FFMPEG_PATH`, default `/usr/bin/ffmpeg`).
