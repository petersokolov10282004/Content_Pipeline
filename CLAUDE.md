# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

ContentPipeline is a **generic content automation pipeline platform**. It is not a single-purpose video tool — new pipeline types are added by implementing `PipelineStepHandler` + a Temporal Workflow without touching existing code. The first pipeline implemented is a short-form story gameplay video: prompt → AI script → AI subtitles → FFmpeg render → YouTube upload.

Monorepo layout:
- `backend/` — Spring Boot 3.3 / Java 21 / Maven (also has a `Dockerfile`)
- `frontend/` — Next.js 14 App Router / TypeScript / TailwindCSS (not containerized — runs via Node)
- `docker-compose.yml` — PostgreSQL 16 + Temporal server + UI + backend (+ Redis via profile)

**Build status:** Phases 1–4 complete. All four story step handlers exist and run synchronously; SSE events (incl. `STEP_PROGRESS`) and run-status transitions are wired. Story/subtitle generation is currently **stubbed** (hardcoded text — see `TODO(llm)` in the two handlers) so the pipeline runs without `ANTHROPIC_API_KEY`. Render runs **real FFmpeg** (binary is in the backend image) and needs R2 credentials + an uploaded gameplay asset to succeed; YouTube upload is **mocked** (writes a MOCK `PublishResultArtifact`). Without R2/gameplay, a run fails *loudly* at the render step (never hangs).

## Development Commands

### Running the whole app (Docker)
The compose plugin on this machine (Ubuntu 24.04) is **`docker-compose-v2`** (install: `sudo apt install docker-compose-v2`), invoked as `docker compose` (space, not hyphen).
```bash
docker compose up -d                   # start Postgres + Temporal + Temporal UI + backend
docker compose up -d --build backend   # rebuild & restart backend after Java changes
docker compose --profile redis up -d   # also start Redis (optional)
docker compose ps                      # status (Postgres shows "healthy")
docker compose logs -f backend         # follow backend logs (look for "Started ContentPipelineApplication")
docker compose down                    # stop all (DB volume survives); add -v to wipe data
```
- Temporal UI: http://localhost:8088 · API health: http://localhost:8080/actuator/health
- Compose overrides `DB_URL`/`TEMPORAL_HOST` to Docker service names, so the `.env` `localhost` values are only used when running the backend outside Docker.
- The **frontend is not in Docker.** Run it separately (see Frontend section), then open http://localhost:3000.

### Backend
There is **no system `mvn` and no system `java` toolchain on PATH for Maven** — use the committed `backend/mvnw` wrapper (it downloads Maven 3.9.9 to `~/.m2/wrapper` on first run). Java 21 is installed.
```bash
cd backend
./mvnw spring-boot:run                          # run with dev defaults (needs infra up)
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod  # prod profile
./mvnw clean package                            # build JAR
./mvnw clean package -DskipTests                # build JAR, skip tests
./mvnw test                                     # all tests
./mvnw test -Dtest=SomeTestClass                # single test class
./mvnw test -Dtest=SomeTestClass#methodName     # single test method
./mvnw flyway:info                              # check migration status
```
A clean `mvn package` can pass while the app still fails **at startup** (e.g. bean wiring, lazy loading) — after backend changes, verify by booting (`docker compose up -d --build backend` then check the logs), not just compiling.

### Frontend
There is **no system `node`/`npm`/`npx` on PATH.** Use Cursor's bundled Node v22 at `/usr/share/cursor/resources/app/resources/helpers/node`. Dependencies are already installed (`node_modules` present, installed via a bootstrapped npm). Run scripts by calling the binaries directly:
```bash
cd frontend
NODE=/usr/share/cursor/resources/app/resources/helpers/node
"$NODE" node_modules/.bin/next dev          # dev server on :3000
"$NODE" node_modules/.bin/next build        # production build
"$NODE" node_modules/typescript/bin/tsc --noEmit   # type-check
"$NODE" node_modules/.bin/next lint         # eslint
```
This project targets **Next 14**: config is `next.config.mjs` (NOT `.ts` — TS config needs Next 15), and fonts come from `next/font/google` names available in 14 (e.g. `Inter`, not `Geist`).

### Environment
Copy `.env.example` to `.env` (Docker Compose reads it via `env_file`). The backend **boots fine with blank credentials** — they're only exercised when a pipeline actually calls out: `ANTHROPIC_API_KEY` (Phase 4 Claude steps), `R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`/`R2_ENDPOINT` (asset upload/render). Fill them when testing those flows. Frontend reads `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`).

Controllers resolve the caller from the `X-Dev-User-Id` header, defaulting to `dev-user-001` (`common/model/DevUser`) — the frontend `lib/api/client.ts` sends the same value, so curl (header-less) and the UI see the same data.

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

Pipelines run as Temporal workflows (`workflow/StoryPipelineWorkflowImpl.java`). Each step is a Temporal Activity. Temporal provides crash recovery, retry with backoff, and per-step visibility via its UI.

- Workers run in the same JVM as the API server. They can be extracted to a separate process later.
- **Steps run synchronously inside the activity** (current model). `RenderVideoStepHandler` downloads the gameplay asset from R2, runs FFmpeg, uploads the MP4 back to R2, and returns `{"renderedVideo": <artifactId>}` — all inline. `UploadVideoStepHandler` does a mock YouTube upload inline. There is **no** async job/worker/polling layer: the old `RenderJob`/`UploadJob` queued-work design and its imaginary `FfmpegRenderWorker`/`@Scheduled` workers were never implemented and have been removed. Extracting long steps back into async workers (with Temporal heartbeats) is a future optimization, not the current design — don't reintroduce `RenderJob`/`UploadJob`.

**Worker wiring (deliberate):** we use only the core `temporal-sdk` dependency, **not** `temporal-spring-boot-starter`. The starter eagerly connects to Temporal and starts workers at boot (and reads `spring.temporal.*`), which breaks startup when Temporal isn't reachable. Instead `config/TemporalConfig.java` creates the `WorkflowServiceStubs`/`WorkflowClient` (lazy stubs) and, in the `workerFactory` bean, registers `StoryPipelineWorkflowImpl` + `PipelineActivitiesImpl` on the `story-pipeline` task queue and calls `factory.start()`. New workflows/activities are registered there.

**Run lifecycle:** `PipelineRunService.create` persists the `PipelineRun` + all `PipelineStepRun` rows (PENDING) *before* starting Temporal, so the IDs exist when the workflow queries them. The ordered step-run IDs are passed to the workflow via `WorkflowInput`. `PipelineActivitiesImpl.executeStep` marks a step RUNNING, runs the handler, then COMPLETED — and on **any** exception marks it FAILED (never leaves it RUNNING). The workflow calls `completeRun(success)` at the end, which flips `PipelineRun.status` to COMPLETED or STEP_FAILED and closes the SSE stream.

**Step observability (debugging):** each `PipelineStepRun` carries a `phase` string + `lastHeartbeatAt` timestamp. A handler reports progress via `StepContext.progress().report("RUNNING_FFMPEG")`, which updates the step row and emits an SSE `STEP_PROGRESS` event. This makes it visible *where inside a step* execution is — a stale `lastHeartbeatAt` means a stuck step, and the `phase` pinpoints the stall (e.g. `DOWNLOADING_GAMEPLAY` vs `UPLOADING_RENDER`). The `StoryPipelineWorkflowImplTest` (Temporal `TestWorkflowEnvironment`) asserts the workflow always terminates and that `renderedVideo` flows from render into upload — a regression/anti-deadlock guard.

### Artifact Polymorphism

All artifact types share a single `artifacts` table (`SINGLE_TABLE` JPA inheritance, `artifact_type` discriminator column). Subtypes: `SCRIPT`, `SUBTITLE`, `RENDER_CONFIG`, `RENDERED_VIDEO`, `PUBLISH_CONFIG`, `PUBLISH_RESULT`. Each subtype's columns are sparse (null for other types). Adding a new artifact type = new `@Entity @DiscriminatorValue("X") class XArtifact extends Artifact`.

### Media Storage: Cloudflare R2

Assets and rendered videos are stored in R2 (S3-compatible). The PostgreSQL entity only stores the `storageKey` + `storageBucket`. Files are never served through the API — all access uses presigned URLs from `StorageService.generatePresignedDownloadUrl()`. Client uploads go directly to R2 via presigned PUT URLs; the backend confirms with a `confirm-upload` endpoint.

R2 key conventions:
- Assets: `assets/{projectId}/{assetId}/{filename}`
- Renders: `renders/{projectId}/{runId}/{stepRunId}/{uuid}.mp4`

**HTTP client (deliberate):** AWS SDK 2.46 defaults its sync client to Apache HttpClient 5.4+, which conflicts with the 5.3.x that Spring Boot 3.3.4 manages (`ClassNotFoundException: TlsSocketStrategy`). So the `s3` dependency excludes `apache-client`/`apache5-client`, `url-connection-client` is added, and `StorageConfig` sets `.httpClient(UrlConnectionHttpClient.create())` on the `S3Client`. The `S3Presigner` needs no HTTP client (it only signs). The asset upload key is generated *after* the row is flushed (to get the assetId) since `storage_key` is `NOT NULL`; client-supplied filenames are sanitized into the key.

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

**Lazy-loading rule (`open-in-view: false`):** because Open-Session-In-View is disabled, a lazy `@OneToMany`/`@ManyToOne` accessed *outside* a transaction throws `LazyInitializationException` (HTTP 500). Any `*Response.from(entity)` mapper that touches a lazy association must either (a) run inside a `@Transactional(readOnly = true)` service method, or (b) be fed an entity loaded with `@EntityGraph(attributePaths = ...)`. Controllers that map entities to DTOs directly from a repository (e.g. `PipelineTemplateController`) rely on `@EntityGraph` finders. Prefer keeping mapping inside the `@Transactional` service.

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
