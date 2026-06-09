# Backend Testing — Context & Continuation

> Handoff notes for the backend test effort. Read this first when resuming.

## TL;DR — where we are

- **Suite is GREEN under JDK 21 in Docker: 58 tests, 0 failures, 0 errors** (last run 2026-06-08). Old + new all pass.
- Original 5 new classes plus, since then: `PipelineTemplateResponseTest` (mapper/lazy-load hazard) and two `AssetServiceTest` `downloadUrl` cases.
- **Why Docker:** this machine has **only Java 25** on PATH; Mockito's inline mock maker cannot instrument classes under JDK 25, so `./mvnw test` locally errors out (`Mockito cannot mock this class` / `Could not modify all classes`). This is an environment mismatch, **not** a test-logic problem. The project targets **Java 21**, and the `Dockerfile` build stage pins `maven:3.9.9-eclipse-temurin-21` — so tests must run under JDK 21, which Docker provides.

## How to run the tests (disposable JDK 21 container)

Chosen approach: a throwaway Maven container (same image as the Dockerfile build stage). Does **not** modify the Dockerfile; production image stays `-DskipTests` and lean.

```bash
cd /home/peter/projects/Content_Pipeline/backend
docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 \
  -w /app maven:3.9.9-eclipse-temurin-21 mvn test
```

Single class / method:
```bash
# ... same docker run ... mvn test -Dtest=PipelineActivitiesImplTest
# ... same docker run ... mvn test -Dtest=ProjectServiceTest#updateAppliesOnlyNonNullFields
```

### Docker access note — SOLVED, no session restart needed

`peter` **is** in the `docker` group in `/etc/group` (`getent group docker` → `docker:x:125:peter`), but the running login session predates that change so its *active* process groups don't include `docker` — `docker ps` directly still fails with "socket NOT reachable". A full re-login would fix it, but you don't need one:

**Wrap docker commands in `sg docker -c '...'`** to activate the supplemental group for just that command. This works in the current session without any restart. So the test command is:

```bash
cd /home/peter/projects/Content_Pipeline/backend
sg docker -c 'docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 \
  -w /app maven:3.9.9-eclipse-temurin-21 mvn test'
```

(The plain `docker run ...` form at the top of this file works too **once** the session is re-logged-in; until then prefix with `sg docker -c`.) No passwordless sudo on this box, so `sudo docker ...` is not an option for the agent.

## Baseline expectation

The **pre-existing** tests were already failing locally for the same JDK-25 reason. Under JDK 21 in Docker the whole suite (old + new) is expected to pass. If anything fails under JDK 21, it's a real issue to fix.

## Testing philosophy used here

- **Pure Mockito unit tests** matching the existing convention: `@ExtendWith(MockitoExtension.class)`, `@DisplayName` on every case, AssertJ assertions, a doc-comment header per class explaining *what* is tested and *why* collaborators are mocked, and `ReflectionTestUtils.setField(entity, "id", uuid)` to mimic the ids JPA assigns on persist.
- **No `@SpringBootTest`** — that would boot Flyway + Postgres + Temporal, none of which are available in the test build and none of which are the unit under test. Keeps tests fast and hermetic.

### Components deliberately mocked / set aside (not backend functionality)

| Component | Why excluded | How handled |
|---|---|---|
| **Temporal** (`WorkflowClient`) | External workflow engine | Mocked; static `WorkflowClient.start` neutralized via `mockStatic(WorkflowClient.class, inv -> null)` |
| **Cloudflare R2 / AWS S3** (`S3Client`, `S3Presigner`) | External object storage | Mocked |
| **SSE** (`SseEmitterRegistry`) | Code comment in the class says it's being temporarily replaced by the HTML test harness | Skipped intentionally |
| **Anthropic / YouTube** | Phase 4 — step handlers don't exist yet | Nothing to test |

## New test classes

| Test | Target | Failure points covered |
|---|---|---|
| `workflow/PipelineActivitiesImplTest.java` | `PipelineActivitiesImpl#executeStep` (Temporal↔handler bridge) | **The "never left RUNNING" invariant** (CLAUDE.md "Run lifecycle"): step marked FAILED on handler `StepExecutionException`, on missing-handler `PipelineException`, and on unexpected `RuntimeException`; happy-path PENDING→RUNNING→COMPLETED with timestamps + returned artifact map; `StepContext` wiring (project id from run, both input maps forwarded); fail-fast on unknown `stepRunId` / `pipelineRunId` before any handler runs. |
| `common/storage/R2StorageServiceTest.java` | `R2StorageService` | `exists()` returns `true` on head success, `false` **only** on `NoSuchKeyException`, and **propagates** any other `AwsServiceException` (a transient/auth error must not masquerade as "object absent", which would let confirm-upload pass falsely); presign helpers honour the requested expiry `Duration` and return the signed URL; `delete` forwards exact bucket/key. |
| `project/service/ProjectServiceTest.java` | `ProjectService` | **404-not-403 ownership leak guard** in `requireOwnedProject` (foreign-owned and missing both look like 404); `update` partial-update semantics (null fields preserved, status-only change works); `create` stamps owner + `ACTIVE`; `delete`/`get` happy paths. |
| `workflow/WorkflowStarterTest.java` | `WorkflowStarter#start` | Deterministic workflow id `story-pipeline-<runId>` (CLAUDE.md duplicate-start safety) via captured `WorkflowOptions`; correct task queue (`WorkflowStarter.TASK_QUEUE`); distinct run ids → distinct workflow ids. |
| `pipeline/run/api/dto/PipelineRunResponseTest.java` | `PipelineRunResponse#from` | The `open-in-view:false` lazy-load hazard (CLAUDE.md): given a fully-initialized graph, every run field is carried across, template name resolved, step-runs mapped in order; a run with no steps maps to an empty (non-null) list. |

## Known gotchas baked into the tests (don't "fix" these)

- `StepExecutionException` has **no** `(String)` constructor — it's `(String message, boolean retryable)` and is a **checked** exception.
- `PipelineActivitiesImpl` rethrows via Temporal's `Activity.wrap`, which off a worker thread wraps the cause in a `RuntimeException`. Tests assert on the **root cause** (via a `rootCause()` helper), not the wrapper type.
- `WorkflowClient.start(...)` is overloaded; matching a specific overload with `any(), any()` is ambiguous. We stub the **whole class** to no-op (`mockStatic(WorkflowClient.class, inv -> null)`) rather than the specific method — instance `newWorkflowStub` stubbing is unaffected.
- `ProjectStatus` has exactly two values: `ACTIVE`, `ARCHIVED`.

## Recently added (since the original 5)

- `pipeline/template/api/dto/PipelineTemplateResponseTest.java` — `PipelineTemplateResponse#from` mapper, same lazy-load-hazard rationale as `PipelineRunResponseTest`: full graph → every field carried, steps mapped in list order; inactive/empty-steps maps to `active=false` + empty (non-null) list.
- `AssetServiceTest` — `downloadUrl` happy path (presigns GET for the asset key, 60-min expiry bounded by the call window) and its cross-project 404 guard (no presign requested).

## Not yet covered (candidate next steps)

- Controller-layer header resolution (`X-Dev-User-Id` default `dev-user-001`) — would need `@WebMvcTest` (boots a slice; decide if worth it vs. the no-Spring-context rule above).
- `StoryPipelineWorkflowImpl` orchestration — Temporal's `TestWorkflowEnvironment` would be the proper tool; heavier, deferred.
- `GlobalExceptionHandler` already covered for the three custom mappings; bean-validation (400 on `MethodArgumentNotValidException`) is not.
