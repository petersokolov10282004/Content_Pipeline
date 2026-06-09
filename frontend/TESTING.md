# Frontend Testing

Unit/component tests with **Vitest** + **Testing Library**, run in a disposable
`node:20-alpine` container (matching the backend's Docker-based test story — no local
Node 20 required).

## Run the suite

`node_modules` is cached in a named Docker volume so only the first run installs deps.

```bash
cd /home/peter/projects/Content_Pipeline/frontend
sg docker -c 'docker volume create cp-frontend-node-modules >/dev/null && docker run --rm \
  -v "$PWD":/app \
  -v cp-frontend-node-modules:/app/node_modules \
  -w /app node:20-alpine sh -c "npm install --no-audit --no-fund --silent && npm test"'
```

Single file:
```bash
# ... same docker run ... sh -c "npm install --silent && npm test -- src/lib/sse/pipelineEventSource.test.ts"
```

(`sg docker -c '...'` activates the docker group for the current session — see backend
`TESTING.md` for why. `sudo docker ...` works too.)

## Why Docker

The host has Node 18 on PATH; the project targets Node 20 (the Dockerfile uses
`node:20-alpine`). Running tests in the same image avoids host/toolchain drift — the
same class of problem that forced the backend into a JDK-21 container.

## What's covered (55 tests, 9 files)

| File | Target | Focus |
|---|---|---|
| `lib/sse/pipelineEventSource.test.ts` | `PipelineEventSource` | URL construction, event dispatch, malformed-JSON tolerance, terminal-event auto-close (`RUN_COMPLETED`/`RUN_FAILED`), reconnect-with-backoff, no-reconnect-after-close, listener add/unsubscribe. Uses a fake `EventSource` (jsdom has none). |
| `hooks/usePipelineEvents.test.tsx` | `usePipelineEvents` | Opens only for active runs (skips terminal/missing ids), invalidates the run + runs-list queries on event, closes on unmount. Mocks the event-source module. |
| `hooks/usePipelines.test.ts` | `pipelineKeys` | TanStack Query key shapes — guards against the hook and the invalidator drifting apart. |
| `lib/api/client.test.ts` | `api` / `ApiError` | Dev-user header + JSON content-type injection, body serialization, 204 handling, typed error mapping (incl. non-JSON error bodies). Stubs `fetch`. |
| `lib/api/projects.test.ts` | `ensureDefaultProject` | Named-project / any-active / create-fallback branches; create only as last resort. |
| `lib/api/assets.test.ts` | `inferAssetType`, `putToPresignedUrl` | MIME→AssetType mapping; XHR upload (method/header/body, progress fraction, non-2xx + network-error rejection). Fake `XMLHttpRequest`. |
| `components/ui/StatusBadge.test.tsx` | `StatusBadge` | Renders all run/step statuses; **falls back to a neutral style for an unmapped status instead of crashing**. |
| `lib/utils/formatters.test.ts` | formatters | Duration, file-size unit boundaries, relative-time buckets (fake timers), date rendering. |
| `lib/utils/cn.test.ts` | `cn` | clsx conditionals + tailwind-merge conflict resolution. |

## Conventions

- Vitest globals enabled (`describe`/`it`/`expect` need no import); `vitest.setup.ts`
  registers jest-dom matchers and auto-`cleanup()` between tests.
- `@/` path alias resolved in `vitest.config.ts` (manual alias — not the ESM-only
  `vite-tsconfig-paths`, which can't be `require`d from a CJS-loaded config).
- External boundaries are faked, not hit: `fetch`, `EventSource`, `XMLHttpRequest`,
  and the `./client` / event-source modules are mocked per-test.
