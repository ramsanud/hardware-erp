-- Platform Admin Console, CR-057 phase 3 (System Health & Incident Monitoring).
--
-- job_execution_log is the single source of truth behind BOTH the Developer
-- Tools "Background Jobs" screen and the System Health screen's "last
-- checked / last failure / error count" fields - a scheduled health check
-- for one service (job_name = 'health:database', 'health:whatsapp', ...)
-- is recorded exactly the same way a real business job
-- ('token-cleanup', 'reminder-scheduler') is. One mechanism, not two
-- parallel logging tables for the same concept.
CREATE TABLE job_execution_log (
    job_execution_log_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name              VARCHAR(60) NOT NULL,
    started_at            TIMESTAMP(3) NOT NULL,
    finished_at           TIMESTAMP(3),
    status                VARCHAR(20) NOT NULL,
    duration_ms           BIGINT,
    detail                VARCHAR(1000),
    CONSTRAINT ck_job_execution_log_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_job_execution_log_name_started ON job_execution_log (job_name, started_at DESC);

-- platform_incident - no FK on resolved_by, deliberately: mirrors
-- platform_audit_log.admin_id (V39) and the REQUIRES_NEW transaction-
-- ordering lesson that taught that pattern in CR-054. One OPEN/INVESTIGATING
-- incident per service is the working assumption (enforced in the service
-- layer, not a DB constraint, since RESOLVED/IGNORED incidents for the same
-- service must be allowed to coexist historically).
CREATE TABLE platform_incident (
    platform_incident_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service               VARCHAR(50) NOT NULL,
    severity              VARCHAR(20) NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    description           VARCHAR(2000),
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    first_seen            TIMESTAMP(3) NOT NULL,
    last_seen             TIMESTAMP(3) NOT NULL,
    occurrence_count      INT NOT NULL DEFAULT 1,
    resolved_at           TIMESTAMP(3),
    resolved_by           BIGINT,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    updated_at            TIMESTAMP(3),
    CONSTRAINT ck_platform_incident_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_platform_incident_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'IGNORED'))
);

CREATE INDEX idx_platform_incident_status ON platform_incident (status);
CREATE INDEX idx_platform_incident_service ON platform_incident (service);
