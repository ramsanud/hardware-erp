-- =====================================================================
-- CR-027 : per-tenant subscription tier (FREE/PRO/MAX). Feature gating
-- only - no billing/payment-gateway integration exists, so the tier is
-- self-declared by the shop owner via Shop Settings for now (see
-- SubscriptionService and TenantSettingsServiceImpl). Every table and
-- feature that already existed before this CR stays available on every
-- tier - only the brand-new Notification and AI Assistant features are
-- gated, so no existing tenant loses anything it already relies on.
-- =====================================================================

ALTER TABLE tenant
    ADD COLUMN subscription_tier VARCHAR(10) NOT NULL DEFAULT 'FREE';

ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_subscription_tier
        CHECK (subscription_tier IN ('FREE', 'PRO', 'MAX'));
