# Step Handler Test Harness

Zero-infrastructure testing for the story pipeline step handlers.
No Temporal, no PostgreSQL, no R2 — everything runs in-memory.

## Quick start

```bash
cd backend

# Run the full pipeline walkthrough (prints clean INPUT/OUTPUT blocks in order)
./mvnw -q test -Dtest=StepWalkthroughTest

# Run the per-handler isolation suites
./mvnw -q test -Dtest=GenerateStoryRunnerTest
./mvnw -q test -Dtest=GenerateSubtitlesRunnerTest
./mvnw -q test -Dtest=RenderVideoRunnerTest
./mvnw -q test -Dtest=UploadVideoRunnerTest

# Run all harness tests at once
./mvnw -q test -Dtest="StepWalkthroughTest,GenerateStoryRunnerTest,GenerateSubtitlesRunnerTest,RenderVideoRunnerTest,UploadVideoRunnerTest"
```

## What each test does

| Test class | Purpose |
|---|---|
| `StepWalkthroughTest` | Runs all four handlers **in pipeline order**, threading real output artifacts into the next step's input (mirrors `StoryPipelineWorkflowImpl`). Prints labelled `INPUT/OUTPUT` blocks. Use this to validate the full data handoff by eye. |
| `GenerateStoryRunnerTest` | GENERATE_STORY in isolation: `targetDurationSeconds` from config, default duration fallback, project-not-found guard. |
| `GenerateSubtitlesRunnerTest` | GENERATE_SUBTITLES in isolation: script → subtitle conversion, missing-script guard, unresolved-id guard. |
| `RenderVideoRunnerTest` | RENDER_VIDEO in isolation: fake-ffmpeg end-to-end (POSIX only), missing subtitles/gameplay guards, unresolved asset id guard. |
| `UploadVideoRunnerTest` | UPLOAD_VIDEO in isolation: MOCK publish with configured privacy, default-privacy fallback, missing renderedVideo guard. |

## Platform note (RenderVideoRunnerTest / StepWalkthroughTest)

The render step calls a real process. On POSIX systems the harness swaps the ffmpeg binary for a tiny shell script that writes fake bytes to the output path — no ffmpeg installation required. On non-POSIX platforms (Windows) the render test and the step 3/4 portion of the walkthrough are **automatically skipped** via `assumeTrue(File.separatorChar == '/')`. All other steps run on any platform.

## How the harness works

`StepHarness` (in this package) provides:

- **In-memory repositories** — Mockito stubs for `ArtifactRepository`, `AssetRepository`, `ProjectRepository`. The `save()` stubs stamp a random UUID onto the entity (standing in for `@GeneratedValue`) and store it in an in-memory map.
- **In-memory object storage** — `InMemoryStorage` implements `StorageService` backed by a `Map<String, byte[]>`. The same `StorageProperties` record is wired into the handler under test.
- **Seeding helpers** — `newProject()`, `seedScript(project, text, durationSec)`, `seedSubtitle(project, srtContent)`, `seedRenderedVideo(project)`, `seedGameplayAsset(project, bytes)`.
- **Fluent context builder** — `harness.context(project).artifact("script", id).config("key", "val").build()` produces a `StepContext`.
- **Fake ffmpeg** — `harness.fakeFfmpegWritingOutput()` writes a temp shell script that ignores all arguments and writes `fake-mp4-bytes` to the last argument (the output path). Inject it with:
  ```java
  ReflectionTestUtils.setField(handler, "ffmpegPath", harness.fakeFfmpegWritingOutput());
  ReflectionTestUtils.setField(handler, "tempDir", tempDir.toString());
  ```
- **Progress capture** — every `StepContext` built by the harness records `progress.report(phase)` calls into `harness.phases()`. Clear between steps with `harness.phases().clear()`.
- **Print helpers** — `harness.printInput(handlerKey, ctx)` and `harness.printResult(result)` emit formatted `══ HANDLER_KEY ══ / ── INPUT ── / ── OUTPUT ──` blocks to stdout.

## Wiring a handler under test

```java
StepHarness harness = new StepHarness();

// handlers that only need repos
GenerateStoryStepHandler handler = new GenerateStoryStepHandler(
    harness.artifactRepository(), harness.projectRepository());

// the render handler also needs storage
RenderVideoStepHandler renderHandler = new RenderVideoStepHandler(
    harness.assetRepository(), harness.artifactRepository(),
    harness.storageService(), harness.projectRepository(),
    harness.storageProperties());
ReflectionTestUtils.setField(renderHandler, "ffmpegPath", harness.fakeFfmpegWritingOutput());
ReflectionTestUtils.setField(renderHandler, "tempDir", Files.createTempDirectory("renders").toString());
```

## Adding a new step handler test

1. Create `<HandlerName>RunnerTest.java` alongside the existing runner tests.
2. Construct `StepHarness` in `@BeforeEach`.
3. Instantiate your handler with `harness.*Repository()` / `harness.storageService()`.
4. Seed whatever upstream artifacts the step needs with `harness.seed*()`.
5. Call `harness.context(project).<wiring>.build()` to get a `StepContext`.
6. Run `handler.execute(ctx)` and assert on `result.outputArtifactIds()` + the stored entities.
7. Add `@DisplayName("HANDLER_KEY: …")` to each test for readable output.
