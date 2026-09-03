-- Platform Admin Console, CR-057 phase 11 (Backup Center). This app has no
-- automated database-snapshot infrastructure (no S3, no scheduled backup
-- job) - building a fake "last successful backup" dashboard for that would
-- violate the spec's own "no fake data" rule. What IS real and buildable:
-- an on-demand export of a tenant's core business data, triggered and
-- logged here. The file itself is generated fresh on every request, never
-- stored - this table is a log of who exported what and when, not a blob
-- store, so there is nothing here implying a retention policy that
-- doesn't exist.
CREATE TABLE platform_tenant_export (
    platform_tenant_export_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES tenant(tenant_id),
    -- Deliberately not a foreign key, same pattern as platform_audit_log.admin_id (V39).
    admin_id       BIGINT,
    format         VARCHAR(10) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    record_count   INTEGER,
    file_size_bytes BIGINT,
    error_detail   VARCHAR(500),
    created_at     TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_pte_format CHECK (format IN ('JSON', 'CSV')),
    CONSTRAINT ck_pte_status CHECK (status IN ('COMPLETED', 'FAILED'))
);

CREATE INDEX idx_pte_tenant ON platform_tenant_export (tenant_id, created_at);
