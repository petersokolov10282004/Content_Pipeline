# ContentPipeline — Backend Class Diagram

A structural overview of the backend (`backend/src/main/java/com/contentpipeline`). The
diagrams are [Mermaid](https://mermaid.js.org/) — they render automatically on GitHub and
in VS Code (with a Mermaid preview extension).

The model is split into five views so each stays readable:

1. [Domain entities & relationships](#1-domain-entities) — the persisted data model
2. [Artifact hierarchy](#2-artifact-hierarchy) — single-table polymorphism
3. [The pipeline abstraction](#3-the-pipeline-abstraction) — the extensibility seam
4. [Services & web layer](#4-services--web-layer) — how requests are handled
5. [Workflow & async jobs](#5-workflow--async-jobs) — Temporal + background workers

> Scope note: this reflects classes that exist today. The Phase-4 story step handlers
> (`GENERATE_STORY`, etc.) are seeded as data but their handler classes are not written
> yet, so they appear only as the `PipelineStepHandler` interface, not concrete types.

---

## 1. Domain entities

Every entity extends `BaseEntity` (UUID id + created/updated timestamps). Status fields are
string-valued enums. Lazy `@ManyToOne` associations are marked `LAZY`.

```mermaid
classDiagram
    class BaseEntity {
        <<abstract>>
        +UUID id
        +Instant createdAt
        +Instant updatedAt
    }

    class Project {
        +String name
        +String description
        +String ownerIdentifier
        +ProjectStatus status
    }

    class Asset {
        +String name
        +AssetType assetType
        +String storageKey
        +String storageBucket
        +AssetStatus status
    }

    class PipelineTemplate {
        +String name
        +Integer version
        +Boolean active
    }

    class PipelineStepDefinition {
        +Integer stepOrder
        +String stepHandlerKey
        +String stepName
        +String configJson
        +Boolean retryable
        +Integer maxRetries
    }

    class PipelineRun {
        +PipelineRunStatus status
        +String temporalWorkflowId
        +String inputParamsJson
        +Instant startedAt
        +Instant completedAt
    }

    class PipelineStepRun {
        +Integer stepOrder
        +StepRunStatus status
        +Integer attemptNumber
        +String errorMessage
    }

    class Artifact {
        <<abstract>>
        +Integer version
        +ArtifactStatus status
    }

    class SocialAccount {
        +SocialPlatform platform
        +AccountStatus status
        +String accessToken
    }

    BaseEntity <|-- Project
    BaseEntity <|-- Asset
    BaseEntity <|-- PipelineTemplate
    BaseEntity <|-- PipelineStepDefinition
    BaseEntity <|-- PipelineRun
    BaseEntity <|-- PipelineStepRun
    BaseEntity <|-- Artifact
    BaseEntity <|-- SocialAccount

    Project "1" *-- "many" Asset : owns
    Project "1" *-- "many" PipelineRun : owns
    Project "1" *-- "many" SocialAccount : owns
    Project "1" o-- "many" Artifact : scopes

    PipelineTemplate "1" *-- "many" PipelineStepDefinition : defines (ordered)
    PipelineRun "many" --> "1" PipelineTemplate : instantiates
    PipelineRun "1" *-- "many" PipelineStepRun : tracks (ordered)
    PipelineStepRun "many" --> "1" PipelineStepDefinition : executes
    PipelineRun "many" --> "many" Asset : inputAssets (join table)
```

**How to read it:** a `Project` is the top container. A `PipelineTemplate` is a reusable
blueprint; starting it creates a `PipelineRun` with one `PipelineStepRun` per
`PipelineStepDefinition`. Each step run produces `Artifact`s (scoped to the project, keyed
by `pipelineStepRunId`).

---

## 2. Artifact hierarchy

All artifact types share one `artifacts` table via JPA `SINGLE_TABLE` inheritance; the
`artifact_type` discriminator column selects the subtype. Each subtype's columns are sparse
(null for other types). Adding a type = one new `@DiscriminatorValue` subclass.

```mermaid
classDiagram
    class Artifact {
        <<abstract>>
        +Project project
        +UUID pipelineStepRunId
        +Integer version
        +ArtifactStatus status
    }
    class ScriptArtifact {
        +String scriptText
        +String title
        +String genre
        +Integer estimatedDurationSeconds
    }
    class SubtitleArtifact {
        +String srtContent
        +Integer lineCount
        +Integer totalWords
    }
    class RenderConfigArtifact {
        +String configJson
        +Integer targetWidthPx
        +Integer targetHeightPx
        +Integer targetFps
    }
    class RenderedVideoArtifact {
        +String storageKey
        +Long fileSizeBytes
        +Integer durationSeconds
        +String resolution
    }
    class PublishConfigArtifact {
        +String title
        +String description
        +String privacyStatus
    }
    class PublishResultArtifact {
        +String publishStatus
        +String platformVideoId
        +String platformUrl
        +Instant publishedAt
    }

    Artifact <|-- ScriptArtifact : SCRIPT
    Artifact <|-- SubtitleArtifact : SUBTITLE
    Artifact <|-- RenderConfigArtifact : RENDER_CONFIG
    Artifact <|-- RenderedVideoArtifact : RENDERED_VIDEO
    Artifact <|-- PublishConfigArtifact : PUBLISH_CONFIG
    Artifact <|-- PublishResultArtifact : PUBLISH_RESULT
```

The subtypes line up with the four story steps: `GENERATE_STORY` → `ScriptArtifact`,
`GENERATE_SUBTITLES` → `SubtitleArtifact`, `RENDER_VIDEO` → `RenderConfig`/`RenderedVideo`,
`UPLOAD_VIDEO` → `PublishConfig`/`PublishResult`.

---

## 3. The pipeline abstraction

The single extensibility seam. A new pipeline step = a `@Component` implementing
`PipelineStepHandler`; `StepHandlerRegistry` auto-discovers it by `handlerKey()`. No
existing class changes.

```mermaid
classDiagram
    class PipelineStepHandler {
        <<interface>>
        +handlerKey() String
        +execute(StepContext) StepResult
    }

    class StepContext {
        <<record>>
        +UUID pipelineRunId
        +UUID pipelineStepRunId
        +UUID projectId
        +Map~String,UUID~ inputArtifactIds
        +Map~String,UUID~ inputAssetIds
        +Map~String,String~ stepConfig
        +String devUserId
    }

    class StepResult {
        <<record>>
        +boolean success
        +Map~String,UUID~ outputArtifactIds
        +String errorMessage
        +success(Map) StepResult$
        +failure(String) StepResult$
    }

    class StepHandlerRegistry {
        -Map~String,PipelineStepHandler~ handlers
        +hasHandler(String) boolean
        +getRequired(String) PipelineStepHandler
    }

    class StepExecutionException {
        +boolean retryable
    }

    StepHandlerRegistry o-- "many" PipelineStepHandler : indexes by handlerKey
    PipelineStepHandler ..> StepContext : consumes
    PipelineStepHandler ..> StepResult : produces
    PipelineStepHandler ..> StepExecutionException : throws
```

---

## 4. Services & web layer

REST controllers delegate to `@Transactional` services. The caller identity is the
`X-Dev-User-Id` header (auth seam). Services enforce ownership and 404-not-403 on access.

```mermaid
classDiagram
    class ProjectController
    class AssetController
    class PipelineRunController
    class PipelineTemplateController

    class ProjectService {
        +requireOwnedProject(userId, projectId) Project
    }
    class AssetService {
        +createUploadUrl(...) UploadUrlResponse
        +confirmUpload(...) AssetResponse
        +requireOwnedAsset(...) Asset
    }
    class PipelineRunService {
        +create(...) PipelineRunResponse
        +requireOwnedRun(...) PipelineRun
    }

    class StorageService {
        <<interface>>
        +generatePresignedUploadUrl(...) String
        +generatePresignedDownloadUrl(...) String
        +exists(bucket, key) boolean
    }
    class R2StorageService

    ProjectController --> ProjectService
    AssetController --> AssetService
    PipelineRunController --> PipelineRunService
    PipelineTemplateController --> PipelineTemplateRepository

    AssetService --> ProjectService : ownership check
    AssetService --> StorageService : presign / verify
    AssetService --> AssetRepository
    ProjectService --> ProjectRepository
    PipelineRunService --> ProjectService
    PipelineRunService --> PipelineTemplateRepository
    PipelineRunService --> AssetRepository
    PipelineRunService --> PipelineRunRepository
    PipelineRunService --> WorkflowStarter : hands off run

    StorageService <|.. R2StorageService
```

---

## 5. Workflow & async jobs

Pipelines run as Temporal workflows; each step is an Activity. The activity looks up the
right `PipelineStepHandler` via the registry. Long-running work (render, upload) is offloaded
to DB-backed job rows that scheduled workers claim and process.

```mermaid
classDiagram
    class WorkflowStarter {
        +start(WorkflowInput) String
    }
    class WorkflowInput {
        <<record>>
        +UUID pipelineRunId
        +List~UUID~ stepRunIds
        +Map~String,UUID~ inputAssets
    }
    class StoryPipelineWorkflow {
        <<interface>>
        +run(WorkflowInput)
    }
    class StoryPipelineWorkflowImpl
    class PipelineActivities {
        <<interface>>
        +executeStep(...)
    }
    class PipelineActivitiesImpl

    class RenderJob {
        +RenderJobStatus status
        +UUID scriptArtifactId
        +UUID gameplayAssetId
        +String outputStorageKey
    }
    class UploadJob {
        +UploadJobStatus status
        +UUID renderedVideoArtifactId
    }
    class PipelineStepRun

    WorkflowStarter ..> WorkflowInput : builds
    WorkflowStarter ..> StoryPipelineWorkflow : starts via Temporal
    StoryPipelineWorkflow <|.. StoryPipelineWorkflowImpl
    PipelineActivities <|.. PipelineActivitiesImpl
    StoryPipelineWorkflowImpl ..> PipelineActivities : calls per step
    PipelineActivitiesImpl --> StepHandlerRegistry : resolves handler
    PipelineActivitiesImpl --> PipelineRunRepository
    PipelineActivitiesImpl --> PipelineStepRunRepository

    RenderJob "1" --> "1" PipelineStepRun : one-to-one
    UploadJob "1" --> "1" PipelineStepRun : one-to-one
    UploadJob "many" --> "1" SocialAccount
```

---

### Legend

| Mermaid notation | Meaning |
|---|---|
| `<\|--` | inheritance (extends) |
| `<\|..` | interface realization (implements) |
| `*--` | composition (owns; lifecycle-bound) |
| `o--` | aggregation (references; independent lifecycle) |
| `-->` | association (has-a / uses) |
| `..>` | dependency (transient use: param, return, throws) |
| `"1" / "many"` | multiplicity |
