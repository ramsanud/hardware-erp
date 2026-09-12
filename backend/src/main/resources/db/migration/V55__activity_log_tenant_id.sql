-- =====================================================================
-- CR-072 : activity_log gains the tenant_id it has never had.
--
-- V3 created this table without one and nothing since added it, so the
-- business audit trail - which carries before/after values for roughly
-- ten modules - could never be read back safely. Any shop-wide viewer
-- over the existing repository query would have served every tenant's
-- business changes to any AUDIT_VIEW holder, which is the defect class
-- BUG-SEC-001 already cost this project once.
--
-- NULLABLE, WITH NO DEFAULT, AND THAT IS THE POINT.
--
-- The backfill below can attribute a row only when it was written by a
-- signed-in user. Rows written by a scheduled job or an import carry a
-- null user_id and there is no honest way to recover their owner after
-- the fact.
--
-- Those rows stay null, and null is the SAFE value here: every read
-- filters `tenant_id = :tenantId`, and in SQL `NULL = anything` is never
-- true, so an unattributable row becomes invisible to every tenant
-- rather than visible to all of them. A NOT NULL column would have
-- forced a guess, and inventing an owner for a row in an audit table is
-- worse than admitting the row is orphaned.
-- =====================================================================

ALTER TABLE activity_log ADD COLUMN tenant_id BIGINT;

-- Attribute every row that was written by a real user. app_user.tenant_id
-- is the authority; nothing here trusts activity_log's own contents.
UPDATE activity_log a
SET tenant_id = u.tenant_id
FROM app_user u
WHERE a.user_id = u.user_id
  AND a.tenant_id IS NULL;

-- No FK to tenant. activity_log is history: it must outlive the row it
-- describes, and a tenant removed from the platform must not take its
-- own audit trail with it on cascade. security_audit_log and
-- refresh_token are modelled the same way - see V14's note.
--
-- Tenant first in the index: every read is scoped to one shop before any
-- other predicate applies, so it is the leading column of every query
-- this table will now serve.
CREATE INDEX idx_activity_tenant_created
    ON activity_log (tenant_id, created_at DESC);

CREATE INDEX idx_activity_tenant_module
    ON activity_log (tenant_id, module_code, created_at DESC);
