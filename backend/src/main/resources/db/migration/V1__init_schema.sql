-- ContentPipeline — Initial Schema
-- V1: All core tables for Phase 1

-- ─── Projects ─────────────────────────────────────────────────────────────────
CREATE TABLE projects (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    owner_identifier  VARCHAR(255) NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Assets ───────────────────────────────────────────────────────────────────
CREATE TABLE assets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    asset_type        VARCHAR(100) NOT NULL,
    storage_key       VARCHAR(1024) NOT NULL,
    storage_bucket    VARCHAR(255) NOT NULL,
    content_type      VARCHAR(255),
    size_bytes        BIGINT,
    original_filename VARCHAR(512) NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_assets_project_id  ON assets(project_id);
CREATE INDEX idx_assets_asset_type  ON assets(asset_type);
CREATE INDEX idx_assets_status      ON assets(status);

-- ─── Pipeline Templates ───────────────────────────────────────────────────────
CREATE TABLE pipeline_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    version     INTEGER      NOT NULL DEFAULT 1,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE pipeline_step_definitions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_template_id UUID         NOT NULL REFERENCES pipeline_templates(id) ON DELETE CASCADE,
    step_order           INTEGER      NOT NULL,
    step_handler_key     VARCHAR(100) NOT NULL,
    step_name            VARCHAR(255) NOT NULL,
    description          TEXT,
    config_json          TEXT,
    retryable            BOOLEAN      NOT NULL DEFAULT TRUE,
    max_retries          INTEGER      NOT NULL DEFAULT 3,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_template_step_order UNIQUE (pipeline_template_id, step_order)
);
CREATE INDEX idx_step_defs_template_id ON pipeline_step_definitions(pipeline_template_id);

-- ─── Pipeline Runs ────────────────────────────────────────────────────────────
CREATE TABLE pipeline_runs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id           UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    pipeline_template_id UUID         NOT NULL REFERENCES pipeline_templates(id),
    status               VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    temporal_workflow_id VARCHAR(512) NOT NULL,
    temporal_run_id      VARCHAR(512),
    input_params_json    TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pipeline_runs_project_id           ON pipeline_runs(project_id);
CREATE INDEX idx_pipeline_runs_status               ON pipeline_runs(status);
CREATE INDEX idx_pipeline_runs_temporal_workflow_id ON pipeline_runs(temporal_workflow_id);

CREATE TABLE pipeline_run_input_assets (
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    asset_id        UUID NOT NULL REFERENCES assets(id),
    PRIMARY KEY (pipeline_run_id, asset_id)
);

CREATE TABLE pipeline_step_runs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id             UUID        NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    pipeline_step_definition_id UUID        NOT NULL REFERENCES pipeline_step_definitions(id),
    step_order                  INTEGER     NOT NULL,
    status                      VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    attempt_number              INTEGER     NOT NULL DEFAULT 1,
    error_message               TEXT,
    error_stack_trace           TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_step_runs_pipeline_run_id ON pipeline_step_runs(pipeline_run_id);
CREATE INDEX idx_step_runs_status          ON pipeline_step_runs(status);

-- ─── Artifacts (SINGLE_TABLE inheritance) ─────────────────────────────────────
-- artifact_type discriminator values:
--   SCRIPT, SUBTITLE, RENDER_CONFIG, RENDERED_VIDEO, PUBLISH_CONFIG, PUBLISH_RESULT
CREATE TABLE artifacts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_type        VARCHAR(50)  NOT NULL,
    pipeline_step_run_id UUID         NOT NULL REFERENCES pipeline_step_runs(id),
    project_id           UUID         NOT NULL REFERENCES projects(id),
    version              INTEGER      NOT NULL DEFAULT 1,
    status               VARCHAR(50)  NOT NULL DEFAULT 'PENDING',

    -- ScriptArtifact
    script_text                  TEXT,
    title                        VARCHAR(512),
    genre                        VARCHAR(100),
    estimated_duration_seconds   INTEGER,

    -- SubtitleArtifact
    srt_content    TEXT,
    line_count     INTEGER,
    total_words    INTEGER,

    -- RenderConfigArtifact
    config_json       TEXT,
    output_format     VARCHAR(50),
    target_width_px   INTEGER,
    target_height_px  INTEGER,
    target_fps        INTEGER,

    -- RenderedVideoArtifact
    storage_key    VARCHAR(1024),
    storage_bucket VARCHAR(255),
    file_size_bytes BIGINT,
    duration_seconds INTEGER,
    resolution       VARCHAR(50),

    -- PublishConfigArtifact
    pub_title       VARCHAR(512),
    pub_description TEXT,
    pub_tags        TEXT,
    privacy_status  VARCHAR(50),
    playlist_id     VARCHAR(255),

    -- PublishResultArtifact
    publish_status       VARCHAR(50),
    platform_video_id    VARCHAR(255),
    platform_url         VARCHAR(1024),
    published_at         TIMESTAMPTZ,
    publish_error_message TEXT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_artifacts_step_run_id ON artifacts(pipeline_step_run_id);
CREATE INDEX idx_artifacts_project_id  ON artifacts(project_id);
CREATE INDEX idx_artifacts_type        ON artifacts(artifact_type);

-- ─── Social Accounts ─────────────────────────────────────────────────────────
CREATE TABLE social_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    platform            VARCHAR(50)  NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    platform_account_id VARCHAR(255),
    access_token        TEXT,
    refresh_token       TEXT,
    token_expires_at    TIMESTAMPTZ,
    status              VARCHAR(50)  NOT NULL DEFAULT 'PLACEHOLDER',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_social_accounts_project_id ON social_accounts(project_id);

-- ─── Render Jobs ──────────────────────────────────────────────────────────────
CREATE TABLE render_jobs (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_step_run_id   UUID         NOT NULL UNIQUE REFERENCES pipeline_step_runs(id),
    status                 VARCHAR(50)  NOT NULL DEFAULT 'QUEUED',
    script_artifact_id     UUID         NOT NULL,
    subtitle_artifact_id   UUID         NOT NULL,
    gameplay_asset_id      UUID         NOT NULL,
    output_storage_key     VARCHAR(1024),
    output_storage_bucket  VARCHAR(255),
    claimed_by_worker_host VARCHAR(255),
    claimed_at             TIMESTAMPTZ,
    processing_started_at  TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    attempt_number         INTEGER      NOT NULL DEFAULT 1,
    error_message          TEXT,
    ffmpeg_command_log     TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_render_jobs_status ON render_jobs(status);
CREATE INDEX idx_render_jobs_queued ON render_jobs(created_at) WHERE status = 'QUEUED';

-- ─── Upload Jobs ──────────────────────────────────────────────────────────────
CREATE TABLE upload_jobs (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_step_run_id      UUID        NOT NULL UNIQUE REFERENCES pipeline_step_runs(id),
    status                    VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    rendered_video_artifact_id UUID       NOT NULL,
    publish_config_artifact_id UUID       NOT NULL,
    social_account_id         UUID REFERENCES social_accounts(id),
    started_at                TIMESTAMPTZ,
    completed_at              TIMESTAMPTZ,
    upload_response_json      TEXT,
    error_message             TEXT,
    attempt_number            INTEGER     NOT NULL DEFAULT 1,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_upload_jobs_status ON upload_jobs(status);

-- ─── Publish Configs ─────────────────────────────────────────────────────────
CREATE TABLE publish_configs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id            UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name                  VARCHAR(255) NOT NULL,
    default_title         TEXT,
    default_description   TEXT,
    default_tags          TEXT,
    default_privacy_status VARCHAR(50) NOT NULL DEFAULT 'PRIVATE',
    is_default            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_publish_configs_project_id ON publish_configs(project_id);
