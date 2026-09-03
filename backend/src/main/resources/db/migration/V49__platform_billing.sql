-- Platform Admin Console, CR-057 phase 9 (Subscriptions & Billing). Real
-- Razorpay Orders-API architecture (order creation, client-side payment
-- verification, webhook signature verification) built even though this
-- environment has no live Razorpay credentials - the spec explicitly asks
-- for the architecture rather than a skip. Deliberately scoped to one-time
-- "pay to move tenant.subscription_tier up" checkout, not a recurring
-- Razorpay Subscriptions/auto-renewal engine - that would be its own CR.
CREATE TABLE platform_subscription_order (
    platform_subscription_order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         BIGINT NOT NULL REFERENCES tenant(tenant_id),
    requested_tier    VARCHAR(10) NOT NULL,
    amount_paise      BIGINT NOT NULL,
    currency          VARCHAR(3) NOT NULL DEFAULT 'INR',
    razorpay_order_id VARCHAR(64) NOT NULL UNIQUE,
    status            VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at        TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    created_by        BIGINT,
    updated_at        TIMESTAMP(3),
    updated_by        BIGINT,
    CONSTRAINT ck_psord_tier CHECK (requested_tier IN ('FREE', 'PRO', 'MAX')),
    CONSTRAINT ck_psord_status CHECK (status IN ('CREATED', 'PAID', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_psord_tenant ON platform_subscription_order (tenant_id);
CREATE INDEX idx_psord_created_at ON platform_subscription_order (created_at);

-- One row per Razorpay payment event actually applied. UNIQUE
-- (razorpay_payment_id) is the idempotency guard: Razorpay redelivers
-- webhooks, and the same payment can also arrive via the client-side
-- /verify call before the webhook lands - whichever writes first wins, the
-- second insert attempt is caught in the service layer and treated as
-- "already recorded", never applied twice.
CREATE TABLE platform_subscription_payment (
    platform_subscription_payment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_order_id BIGINT NOT NULL REFERENCES platform_subscription_order(platform_subscription_order_id),
    razorpay_payment_id   VARCHAR(64) NOT NULL UNIQUE,
    razorpay_signature    VARCHAR(255),
    amount_paise          BIGINT NOT NULL,
    status                VARCHAR(20) NOT NULL,
    source                VARCHAR(20) NOT NULL,
    captured_at           TIMESTAMP(3),
    created_at            TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    created_by            BIGINT,
    updated_at            TIMESTAMP(3),
    updated_by            BIGINT,
    CONSTRAINT ck_pspay_status CHECK (status IN ('CAPTURED', 'FAILED')),
    CONSTRAINT ck_pspay_source CHECK (source IN ('CLIENT_VERIFY', 'WEBHOOK'))
);

CREATE INDEX idx_pspay_order ON platform_subscription_payment (subscription_order_id);
