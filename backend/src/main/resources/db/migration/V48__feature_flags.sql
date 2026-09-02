-- Platform Admin Console, CR-057 phase 8 (Feature Flags). A real,
-- backend-enforceable flag store - FeatureFlagService.isEnabled() is the
-- one place any future backend code checks a flag; nothing here is a
-- frontend-only toggle (spec's own "do not rely on frontend flags").
CREATE TABLE feature_flag (
    feature_flag_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flag_key         VARCHAR(100) NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    description      VARCHAR(1000),
    enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    scope            VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    created_at       TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMP(3),
    -- No FK, deliberately - mirrors platform_audit_log.admin_id (V39).
    updated_by       BIGINT,
    CONSTRAINT ck_feature_flag_scope CHECK (scope IN ('GLOBAL', 'TENANT', 'PLAN'))
);
