-- Platform Admin Console, CR-057 phase 12 (Platform Settings - Razorpay).
-- Lets a platform admin fill in real Razorpay credentials from the console
-- itself, without needing to redeploy with new environment variables -
-- the RAZORPAY_* env vars from V49 remain a valid deployment-time way to
-- configure this and stay the fallback if this table's row is absent or
-- disabled (see RazorpayConfigResolver). Singleton row, id always 1 -
-- there is exactly one Razorpay account for this whole platform, not one
-- per tenant (tenants never see or configure this).
CREATE TABLE platform_razorpay_config (
    platform_razorpay_config_id BIGINT PRIMARY KEY DEFAULT 1,
    enabled                BOOLEAN NOT NULL DEFAULT FALSE,
    key_id                 VARCHAR(100),
    -- AES-256-GCM encrypted at the entity boundary (FieldEncryptor, CR-018's
    -- scheme reused as-is) - never plaintext at rest, never returned to the
    -- browser once saved (see RazorpayConfigResponse - configured flags only).
    key_secret_encrypted     VARCHAR(500),
    webhook_secret_encrypted VARCHAR(500),
    pro_plan_amount_paise  BIGINT NOT NULL DEFAULT 99900,
    max_plan_amount_paise  BIGINT NOT NULL DEFAULT 299900,
    updated_at             TIMESTAMP(3),
    -- Deliberately not a foreign key, same pattern as platform_audit_log.admin_id (V39).
    updated_by             BIGINT,
    CONSTRAINT ck_prc_singleton CHECK (platform_razorpay_config_id = 1)
);
