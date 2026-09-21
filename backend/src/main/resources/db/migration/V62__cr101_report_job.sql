-- =====================================================================
-- CR-101 : async report/document job queue.
--
-- A heavy export (Party Statement over a year, Day Book across a
-- quarter, GSTR-1 for a large tenant) rendering PDF/XLSX on the
-- request thread is the same class of problem CR-086's exporter never
-- had to solve, because its five reports are bounded, single-tenant
-- aggregations. This table lets a slow render happen off-thread while
-- the caller polls status, without touching the synchronous /export
-- endpoints CR-086/CR-087 already shipped and that ReportControllerIT
-- still exercises unmodified.
--
-- The finished file is kept in-row as BYTEA, the same pattern V11/V13/
-- V21 already use for images and supplier-bill uploads: one Postgres
-- database is this app's only storage, and a report file is smaller
-- than the 20MB supplier-bill ceiling that pattern was sized for.
-- No object-storage service is introduced for a file that is deleted
-- after seven days.
--
-- One row's lifecycle: PENDING -> PROCESSING -> COMPLETED | FAILED.
-- error_message is populated only for FAILED, file_data only for
-- COMPLETED - the CHECK constraint below makes an inconsistent status
-- impossible to write rather than merely unlikely.
-- =====================================================================

CREATE TABLE report_job (
    report_job_id   BIGSERIAL     PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL REFERENCES tenant (tenant_id),
    requested_by    BIGINT        REFERENCES app_user (user_id),
    report_type     VARCHAR(40)   NOT NULL,
    format          VARCHAR(10)   NOT NULL,
    params_json     TEXT          NOT NULL DEFAULT '{}',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    file_name       VARCHAR(150),
    file_data       BYTEA,
    file_size_bytes INTEGER,
    error_message   VARCHAR(500),
    created_at      TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(3),
    started_at      TIMESTAMP(3),
    completed_at    TIMESTAMP(3),

    CONSTRAINT ck_report_job_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    -- JSON covers GSTR-1 (Gstr1Service.build), which has no tabular
    -- ReportDocument and is never rasterized to PNG - the renderer refuses
    -- that combination itself, not the database.
    CONSTRAINT ck_report_job_format CHECK (
        format IN ('PDF', 'XLSX', 'CSV', 'PNG', 'JSON')),
    -- A COMPLETED row always carries its file; a FAILED row never does.
    -- PENDING/PROCESSING carry neither yet.
    CONSTRAINT ck_report_job_completion CHECK (
        (status = 'COMPLETED' AND file_data IS NOT NULL AND error_message IS NULL)
        OR (status = 'FAILED' AND file_data IS NULL AND error_message IS NOT NULL)
        OR (status IN ('PENDING', 'PROCESSING') AND file_data IS NULL))
);

-- The only reads are "this tenant's jobs, newest first" and "this
-- tenant's one job by id" (never a bare id - see ReportJobService).
CREATE INDEX idx_report_job_tenant_created ON report_job (tenant_id, created_at DESC);

-- Retention sweep (ReportJobCleanupJob, daily): rows older than 7 days,
-- across every tenant, regardless of status.
CREATE INDEX idx_report_job_created_at ON report_job (created_at);
