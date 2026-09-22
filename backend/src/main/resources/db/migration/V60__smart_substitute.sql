-- =====================================================================
-- CR-089 : Smart Substitute Product Suggestion.
--
-- When a requested product is out of stock, the owner sees in-stock
-- alternatives from the shop's own inventory - manually defined
-- relationships first, then a rule-based similarity score - never an
-- automatic "sell this instead" (spec's own hard rule: only ever
-- "these products may be suitable alternatives").
--
-- Deviation from the brief, stated honestly rather than invented: the
-- spec's "Available Stock = Physical Stock - Reserved Stock - Pending
-- Allocation" formula assumes a stock-reservation mechanism this codebase
-- does not have - Sales Order (CR-052) never reserves physical stock, it
-- is a pre-invoice document only. Availability here is therefore the real,
-- existing `stock.quantity_on_hand` and nothing invented on top of it.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- Structured attributes for substitute matching. Every column nullable -
-- hardware products vary too much to make any of them mandatory (spec's
-- own instruction). `usage_type` avoids the reserved-sounding `usage`.
-- ---------------------------------------------------------------------
ALTER TABLE product
    ADD COLUMN subcategory   VARCHAR(100),
    ADD COLUMN size_label    VARCHAR(50),
    ADD COLUMN material      VARCHAR(100),
    ADD COLUMN color_finish  VARCHAR(100),
    ADD COLUMN shape         VARCHAR(50),
    ADD COLUMN usage_type    VARCHAR(100),
    ADD COLUMN product_type  VARCHAR(100);

COMMENT ON COLUMN product.size_label IS
    'Free text, e.g. "4 Inch" - hardware sizes are not one unit system, never parsed as a number.';

-- Trigram index for typo-tolerant name search ("towr bolt" -> "Tower
-- Bolt") - used by the product-request picker's fuzzy lookup, additive to
-- the existing exact/LIKE search in ProductRepository.search().
CREATE INDEX idx_product_name_trgm ON product USING GIN (product_name gin_trgm_ops);

-- ---------------------------------------------------------------------
-- Manually defined product relationships (spec's "Product Relationship
-- Mapping") - always outrank the rule-based score, and the only path for
-- safety-sensitive categories (electrical, plumbing, locks) where
-- similarity alone must never drive a suggestion.
-- ---------------------------------------------------------------------
CREATE TABLE product_relationship (
    product_relationship_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(tenant_id),
    product_id          BIGINT NOT NULL REFERENCES product(product_id),
    related_product_id  BIGINT NOT NULL REFERENCES product(product_id),
    relationship_type   VARCHAR(20) NOT NULL,
    notes               VARCHAR(255),
    created_at          TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    created_by          BIGINT,
    CONSTRAINT ck_product_relationship_type CHECK (
        relationship_type IN ('ALTERNATIVE', 'COMPATIBLE', 'UPGRADE', 'LOWER_COST', 'SAME_USE', 'REPLACEMENT')),
    CONSTRAINT ck_product_relationship_not_self CHECK (product_id <> related_product_id),
    CONSTRAINT uk_product_relationship UNIQUE (tenant_id, product_id, related_product_id, relationship_type)
);
CREATE INDEX idx_product_relationship_lookup ON product_relationship (tenant_id, product_id);

-- ---------------------------------------------------------------------
-- Per-tenant scoring policy the owner controls (spec: "the owner should
-- be able to configure the minimum recommendation threshold" and "whether
-- products above budget should be displayed"). The scoring WEIGHTS
-- themselves (same-category +30 etc.) are platform config
-- (app.substitute.scoring.*), not a per-tenant row - see
-- SubstituteScoringProperties.
-- ---------------------------------------------------------------------
CREATE TABLE substitute_setting (
    tenant_id            BIGINT PRIMARY KEY REFERENCES tenant(tenant_id),
    min_score_threshold  INT NOT NULL DEFAULT 40,
    show_above_budget     BOOLEAN NOT NULL DEFAULT TRUE,
    max_results          INT NOT NULL DEFAULT 3,
    updated_at           TIMESTAMP(3),
    updated_by           BIGINT,
    CONSTRAINT ck_substitute_setting_score CHECK (min_score_threshold BETWEEN 0 AND 110),
    CONSTRAINT ck_substitute_setting_max_results CHECK (max_results BETWEEN 1 AND 20)
);

-- ---------------------------------------------------------------------
-- A customer's request for a product the shop could not sell them right
-- now, and what the engine (or the owner, by hand) suggested instead.
-- ---------------------------------------------------------------------
CREATE TABLE product_request (
    product_request_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id               BIGINT NOT NULL REFERENCES tenant(tenant_id),
    requested_product_id    BIGINT NOT NULL REFERENCES product(product_id),
    requested_quantity      DECIMAL(18,4) NOT NULL,
    requested_budget_paise  BIGINT,
    customer_name           VARCHAR(200),
    customer_mobile         VARCHAR(15),
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    selected_product_id     BIGINT REFERENCES product(product_id),
    selected_by             BIGINT,
    selected_at             TIMESTAMP(3),
    created_at              TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    created_by              BIGINT,
    resolved_at             TIMESTAMP(3),
    CONSTRAINT ck_product_request_status CHECK (status IN ('OPEN', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_product_request_quantity CHECK (requested_quantity > 0),
    CONSTRAINT ck_product_request_budget CHECK (requested_budget_paise IS NULL OR requested_budget_paise >= 0)
);
CREATE INDEX idx_product_request_tenant ON product_request (tenant_id, status, created_at);
CREATE INDEX idx_product_request_product ON product_request (tenant_id, requested_product_id);

CREATE TABLE product_request_suggestion (
    product_request_suggestion_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_request_id     BIGINT NOT NULL REFERENCES product_request(product_request_id),
    suggested_product_id   BIGINT NOT NULL REFERENCES product(product_id),
    score                   INT NOT NULL,
    match_level             VARCHAR(20) NOT NULL,
    reason                  VARCHAR(500) NOT NULL,
    source                  VARCHAR(20) NOT NULL,
    created_at              TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_prs_match_level CHECK (
        match_level IN ('EXCELLENT', 'HIGH', 'MEDIUM', 'LOW', 'DO_NOT_RECOMMEND')),
    CONSTRAINT ck_prs_source CHECK (source IN ('RULE_BASED', 'MANUAL_MAPPING')),
    CONSTRAINT uk_product_request_suggestion UNIQUE (product_request_id, suggested_product_id)
);
CREATE INDEX idx_prs_request ON product_request_suggestion (product_request_id);

-- ---------------------------------------------------------------------
-- Permissions. Manual mapping create/delete reuses PRODUCT_MANAGE (it is
-- product master data, same footing as editing the product itself) -
-- these two are new because "record what a customer asked for" and
-- "browse/act on that queue" are their own capability, not implied by
-- selling the product.
-- ---------------------------------------------------------------------
INSERT INTO permission (permission_code, permission_name, description, module_code, display_order) VALUES
 ('PRODUCT_REQUEST_VIEW', 'View product requests',
  'See customer product requests and the suggested alternatives for them.',
  'PRODUCT', 40),
 ('PRODUCT_REQUEST_MANAGE', 'Manage product requests',
  'Record a customer product request and select an alternative to offer.',
  'PRODUCT', 41);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r JOIN permission p ON p.permission_code IN ('PRODUCT_REQUEST_VIEW', 'PRODUCT_REQUEST_MANAGE')
WHERE r.role_code IN ('OWNER', 'MANAGER', 'STAFF');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r JOIN permission p ON p.permission_code = 'PRODUCT_REQUEST_VIEW'
WHERE r.role_code = 'ACCOUNTANT';
