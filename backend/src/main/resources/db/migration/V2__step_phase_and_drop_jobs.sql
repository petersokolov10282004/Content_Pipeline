-- Observability: per-step execution phase + liveness heartbeat.
-- `phase` is a free-text marker a handler updates as it progresses
-- (e.g. DOWNLOADING_GAMEPLAY, RUNNING_FFMPEG, UPLOADING_RENDER, MOCK_UPLOAD).
-- `last_heartbeat_at` is bumped on each phase change; a stale value means a stuck step.
ALTER TABLE pipeline_step_runs ADD COLUMN phase VARCHAR(100);
ALTER TABLE pipeline_step_runs ADD COLUMN last_heartbeat_at TIMESTAMPTZ;

-- The async render/upload job queue was never implemented; steps now run
-- synchronously inside the Temporal activity. Drop the vestigial tables.
DROP TABLE IF EXISTS upload_jobs;
DROP TABLE IF EXISTS render_jobs;
