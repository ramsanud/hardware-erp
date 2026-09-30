-- =====================================================================
-- CR-088 : SaaS subscription plans, feature catalogue, plan-feature
-- matrix, per-tenant subscription state and metered usage.
--
-- tenant.subscription_tier (V15, locked values FREE/PRO/MAX) stays the
-- single field every existing gate reads. The three plans map onto it
-- one-to-one - BASIC=FREE, PRO=PRO, PREMIUM=MAX - so nothing that already
-- sets the tier (settings picker, trial coupon, Razorpay verify) has to
-- change shape. What is new is everything AROUND the tier: what a plan
-- costs, which features it carries, whether the subscription is in
-- trial / active / past due / expired / cancelled / suspended, and how
-- much of each metered channel the shop has used this month.
--
-- Every seed INSERT is ON CONFLICT DO NOTHING so a re-run against an
-- environment that already carries the rows is a no-op (spec §23).
-- =====================================================================

CREATE TABLE subscription_plan (
    subscription_plan_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_code            VARCHAR(20)  NOT NULL,
    tier                 VARCHAR(10)  NOT NULL,
    plan_name            VARCHAR(50)  NOT NULL,
    tagline              VARCHAR(200) NOT NULL,
    price_paise          BIGINT       NOT NULL,
    currency             VARCHAR(3)   NOT NULL DEFAULT 'INR',
    billing_period       VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',
    recommended          BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order        INT          NOT NULL DEFAULT 0,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    updated_at           TIMESTAMP(3),
    CONSTRAINT uk_subscription_plan_code UNIQUE (plan_code),
    CONSTRAINT uk_subscription_plan_tier UNIQUE (tier),
    CONSTRAINT ck_subscription_plan_tier CHECK (tier IN ('FREE', 'PRO', 'MAX')),
    CONSTRAINT ck_subscription_plan_period CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT ck_subscription_plan_price CHECK (price_paise >= 0)
);

INSERT INTO subscription_plan (plan_code, tier, plan_name, tagline, price_paise, recommended, display_order) VALUES
 ('BASIC',   'FREE', 'Basic',   'Daily shop operations',                       29900, FALSE, 1),
 ('PRO',     'PRO',  'Pro',     'Automation and advanced business management',  59900, TRUE,  2),
 ('PREMIUM', 'MAX',  'Premium', 'Intelligence, growth and multi-branch',       99900, FALSE, 3)
ON CONFLICT (plan_code) DO NOTHING;

-- ---------------------------------------------------------------------
-- The feature catalogue. FeatureKey.java is the compile-time mirror and
-- FeatureCatalogConsistencyTest asserts the two never diverge.
-- ---------------------------------------------------------------------
CREATE TABLE feature (
    feature_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_key   VARCHAR(50)  NOT NULL,
    feature_name  VARCHAR(100) NOT NULL,
    description   VARCHAR(500) NOT NULL,
    module_code   VARCHAR(30)  NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uk_feature_key UNIQUE (feature_key)
);

INSERT INTO feature (feature_key, feature_name, description, module_code, display_order) VALUES
 -- BASIC ---------------------------------------------------------------
 ('GST_BILLING',          'GST billing',              'GST tax invoices with CGST/SGST/IGST split, PDF, print and download.', 'BILLING', 10),
 ('NON_GST_BILLING',      'Non-GST billing',          'Invoices for unregistered shops or exempt goods.', 'BILLING', 11),
 ('INVOICE_CANCELLATION', 'Invoice cancellation',     'Cancel an issued invoice with a reason; stock and outstanding reverse.', 'BILLING', 12),
 ('PAYMENTS',             'Payments',                 'Cash, UPI, card and bank payments with payment status and receipts.', 'BILLING', 13),
 ('PRODUCTS',             'Products',                 'Product master with code, model, category, brand, prices, stock, barcode and image.', 'PRODUCT', 20),
 ('LOW_STOCK_ALERT',      'Low-stock alert',          'Reorder-level alerts on the dashboard.', 'INVENTORY', 21),
 ('CUSTOMERS',            'Customer management',      'Customers with phone, address, outstanding balance and payment history.', 'CUSTOMER', 30),
 ('SUPPLIERS',            'Supplier management',      'Suppliers, purchase entry, purchase history and supplier balance.', 'SUPPLIER', 40),
 ('INVENTORY',            'Inventory',                'Stock in, stock out, stock adjustment and the basic stock report.', 'INVENTORY', 50),
 ('DASHBOARD',            'Dashboard',                'Today''s sales, purchases, current stock and outstanding amount.', 'DASHBOARD', 60),
 ('BASIC_REPORTS',        'Basic reports',            'Sales, purchase and stock summaries.', 'REPORT', 61),
 ('DATA_EXPORT',          'Data export',              'Export your own shop data at any time, on any plan, even after expiry.', 'SETTINGS', 62),
 ('OFFLINE_SYNC',         'Offline billing',          'Draft invoices offline and sync them safely when the connection returns.', 'BILLING', 63),
 -- PRO -----------------------------------------------------------------
 ('QUOTATION',            'Quotation',                'Quotations that convert to invoices without retyping.', 'SALES', 100),
 ('SALES_ORDER',          'Sales order',              'Sales orders that convert to a delivery challan or invoice.', 'SALES', 101),
 ('DELIVERY_CHALLAN',     'Delivery challan',         'Move goods before the tax invoice is raised.', 'SALES', 102),
 ('CREDIT_NOTE',          'Sales return',             'Credit notes against an invoice; stock and outstanding adjust.', 'SALES', 103),
 ('PURCHASE_RETURN',      'Purchase return',          'Return goods to a supplier against a purchase.', 'PURCHASE', 104),
 ('INVOICE_TEMPLATES',    'Invoice templates',        'Invoice themes and a custom shop logo on every document.', 'BILLING', 105),
 ('THERMAL_PRINT',        'Thermal / Bluetooth print', 'Receipt-width invoice layout for thermal and Bluetooth printers.', 'BILLING', 106),
 ('BULK_IMPORT',          'Bulk product import',      'Excel / CSV product import with preview and confirm.', 'PRODUCT', 110),
 ('PDF_PRICE_IMPORT',     'PDF price-list import',    'Import a supplier''s PDF price list; old vs new price comparison and bulk update.', 'PRODUCT', 111),
 ('PRICE_CHANGE_DETECTION','Price-change detection',  'Price history with increase / decrease indicators.', 'PRODUCT', 112),
 ('STOCK_VALUATION',      'Stock valuation',          'Stock valued at cost with movement history and adjustment audit.', 'INVENTORY', 113),
 ('CUSTOMER_CREDIT',      'Customer credit control',  'Credit limit, outstanding ageing, customer statement and purchase history.', 'CUSTOMER', 120),
 ('PAYMENT_REMINDERS',    'Payment reminders',        'WhatsApp, SMS and email payment reminders.', 'NOTIFICATION', 121),
 ('SUPPLIER_STATEMENT',   'Supplier statement',       'Supplier payment tracking, outstanding and purchase analytics.', 'SUPPLIER', 122),
 ('ADVANCED_REPORTS',     'Advanced reports',         'Profit, GST, expense, top-selling and slow-moving reports; Tally export.', 'REPORT', 130),
 ('STAFF_ROLES',          'Staff roles',              'Multiple staff users with role-based permissions and an activity log.', 'AUTH', 131),
 ('PROJECTS',             'Projects & labour',        'Project material tracking, labour attendance and wage summaries.', 'PROJECT', 132),
 ('COUPONS',              'Coupons',                  'Discount coupons redeemed on invoices.', 'SALES', 133),
 -- PREMIUM -------------------------------------------------------------
 ('SMART_SUBSTITUTE',     'Smart substitute suggestions', 'Alternatives from your own stock when a product is unavailable.', 'SMART', 200),
 ('SMART_INSIGHTS',       'Smart insights',           'Slow-moving, overstock, reorder, demand trend and frequently-bought-together insights.', 'SMART', 201),
 ('ADVANCED_ANALYTICS',   'Advanced analytics',       'Profit, product and customer profitability, trends and cash-flow overview.', 'ANALYTICS', 202),
 ('MULTI_BRANCH',         'Multi-branch',             'Branches with branch-wise stock, sales, purchases, users and stock transfer.', 'BRANCH', 203),
 ('NEARBY_PRODUCT_DISCOVERY','Nearby product discovery', 'Find opted-in nearby shops that may have a product you are out of. Owner-only, never customer-facing.', 'DISCOVERY', 204),
 ('ADVANCED_NOTIFICATIONS','Advanced notifications',  'Daily business summary, purchase reminders and product-request alerts.', 'NOTIFICATION', 205),
 ('AUTO_BACKUP',          'Automatic backup',         'Nightly snapshot of your shop data with history and download.', 'SETTINGS', 206),
 ('AI_FEATURES',          'AI assistant',             'Chat over your own shop data. Metered per request.', 'SMART', 207),
 ('PRIORITY_SUPPORT',     'Priority support',         'Support tickets answered first.', 'SUPPORT', 208)
ON CONFLICT (feature_key) DO NOTHING;

CREATE TABLE plan_feature (
    plan_feature_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_plan_id BIGINT NOT NULL REFERENCES subscription_plan(subscription_plan_id),
    feature_id           BIGINT NOT NULL REFERENCES feature(feature_id),
    created_at           TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uk_plan_feature UNIQUE (subscription_plan_id, feature_id)
);
CREATE INDEX idx_plan_feature_plan ON plan_feature (subscription_plan_id);

-- BASIC carries every feature with display_order < 100, PRO everything
-- below 200, PREMIUM everything. "Everything in the plan below" is a
-- property of the ordering, so a later feature lands in the right plans
-- by its display_order alone.
INSERT INTO plan_feature (subscription_plan_id, feature_id)
SELECT p.subscription_plan_id, f.feature_id
FROM subscription_plan p
JOIN feature f ON (p.plan_code = 'BASIC'   AND f.display_order < 100)
               OR (p.plan_code = 'PRO'     AND f.display_order < 200)
               OR (p.plan_code = 'PREMIUM')
ON CONFLICT (subscription_plan_id, feature_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Metered channels. Included counts are per calendar month; a shop that
-- reaches its included count is refused (USAGE_LIMIT_REACHED), never
-- silently billed onward (spec §15).
-- ---------------------------------------------------------------------
CREATE TABLE plan_usage_limit (
    plan_usage_limit_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_plan_id BIGINT NOT NULL REFERENCES subscription_plan(subscription_plan_id),
    usage_key            VARCHAR(30) NOT NULL,
    included_count       BIGINT NOT NULL,
    created_at           TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    updated_at           TIMESTAMP(3),
    CONSTRAINT uk_plan_usage_limit UNIQUE (subscription_plan_id, usage_key),
    CONSTRAINT ck_plan_usage_key CHECK (usage_key IN ('WHATSAPP', 'SMS', 'EMAIL', 'AI_REQUEST', 'STORAGE_MB')),
    CONSTRAINT ck_plan_usage_included CHECK (included_count >= 0)
);

INSERT INTO plan_usage_limit (subscription_plan_id, usage_key, included_count)
SELECT p.subscription_plan_id, v.usage_key, v.included_count
FROM subscription_plan p
JOIN (VALUES
    ('BASIC',   'WHATSAPP',      50), ('BASIC',   'SMS',      0), ('BASIC',   'EMAIL',   100), ('BASIC',   'AI_REQUEST',    0), ('BASIC',   'STORAGE_MB',   500),
    ('PRO',     'WHATSAPP',     500), ('PRO',     'SMS',    200), ('PRO',     'EMAIL',  1000), ('PRO',     'AI_REQUEST',   50), ('PRO',     'STORAGE_MB',  2000),
    ('PREMIUM', 'WHATSAPP',    2000), ('PREMIUM', 'SMS',   1000), ('PREMIUM', 'EMAIL',  5000), ('PREMIUM', 'AI_REQUEST',  500), ('PREMIUM', 'STORAGE_MB', 10000)
) AS v(plan_code, usage_key, included_count) ON v.plan_code = p.plan_code
ON CONFLICT (subscription_plan_id, usage_key) DO NOTHING;

CREATE TABLE subscription_usage (
    subscription_usage_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             BIGINT NOT NULL REFERENCES tenant(tenant_id),
    usage_key             VARCHAR(30) NOT NULL,
    period_start          DATE NOT NULL,
    used_count            BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    updated_at            TIMESTAMP(3),
    CONSTRAINT uk_subscription_usage UNIQUE (tenant_id, usage_key, period_start),
    CONSTRAINT ck_subscription_usage_key CHECK (usage_key IN ('WHATSAPP', 'SMS', 'EMAIL', 'AI_REQUEST', 'STORAGE_MB')),
    CONSTRAINT ck_subscription_usage_count CHECK (used_count >= 0)
);
CREATE INDEX idx_subscription_usage_tenant ON subscription_usage (tenant_id, period_start);

-- ---------------------------------------------------------------------
-- Per-tenant subscription state. One row per tenant; absent means
-- "ACTIVE on whatever tenant.subscription_tier says, no expiry" so the
-- shops that predate this migration keep working untouched. Gateway
-- references are the Razorpay/Stripe hooks of spec §20 - never card data.
-- ---------------------------------------------------------------------
CREATE TABLE tenant_subscription (
    tenant_subscription_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                BIGINT NOT NULL REFERENCES tenant(tenant_id),
    subscription_plan_id     BIGINT NOT NULL REFERENCES subscription_plan(subscription_plan_id),
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    trial_ends_at            TIMESTAMP(3),
    started_at               TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    ends_at                  TIMESTAMP(3),
    renewal_at               TIMESTAMP(3),
    cancelled_at             TIMESTAMP(3),
    cancellation_reason      VARCHAR(255),
    external_subscription_id VARCHAR(100),
    payment_status           VARCHAR(20),
    payment_reference        VARCHAR(100),
    created_at               TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    created_by               BIGINT,
    updated_at               TIMESTAMP(3),
    updated_by               BIGINT,
    CONSTRAINT uk_tenant_subscription_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_tenant_subscription_status CHECK (
        status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'EXPIRED', 'CANCELLED', 'SUSPENDED'))
);

-- Existing shops: ACTIVE on their current tier. A shop mid-way through a
-- CR-032 coupon trial keeps that as a TRIAL that ends when the coupon does.
INSERT INTO tenant_subscription (tenant_id, subscription_plan_id, status, trial_ends_at, started_at)
SELECT t.tenant_id, p.subscription_plan_id,
       CASE WHEN t.subscription_trial_expires_at IS NULL THEN 'ACTIVE' ELSE 'TRIAL' END,
       t.subscription_trial_expires_at,
       t.created_at
FROM tenant t
JOIN subscription_plan p ON p.tier = t.subscription_tier
ON CONFLICT (tenant_id) DO NOTHING;

-- Every plan or status change, for the audit trail spec §18 asks for.
CREATE TABLE subscription_history (
    subscription_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id               BIGINT NOT NULL REFERENCES tenant(tenant_id),
    from_plan_code          VARCHAR(20),
    to_plan_code            VARCHAR(20) NOT NULL,
    from_status             VARCHAR(20),
    to_status               VARCHAR(20) NOT NULL,
    reason                  VARCHAR(255),
    changed_by              BIGINT,
    created_at              TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_subscription_history_tenant ON subscription_history (tenant_id, created_at);

-- A metered channel refusal is a distinct outcome from a provider failure.
ALTER TABLE notification_log DROP CONSTRAINT ck_notification_log_status;
ALTER TABLE notification_log ADD CONSTRAINT ck_notification_log_status CHECK (
    status IN ('SENT', 'LOGGED_ONLY', 'FAILED', 'DELIVERED', 'READ', 'QUOTA_EXCEEDED'));
