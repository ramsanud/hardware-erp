-- =====================================================================
-- CR-090 : Owner-side Nearby Product Discovery. PREMIUM, opt-in, OFF by
-- default.
--
-- This is the ONE deliberately cross-tenant read in the system, and the
-- privacy rules are structural, not conventional:
--
--   * A shop is invisible to the search unless discovery_enabled AND
--     share_availability are both true - and then only as a coarse
--     AVAILABLE / LIKELY_AVAILABLE bucket, never a quantity or a price.
--   * Its name, phone and approximate location each appear in a result
--     only if that shop's own flag says so; the search SQL selects NULL
--     otherwise (see ShopDiscoveryRepository). Nothing else - prices,
--     suppliers, customers, the rest of its inventory - is in the SELECT
--     list at all.
--   * Coordinates live HERE, on the consent row, not on `tenant`: a shop
--     that never opted in has no stored location to leak.
--   * Every consent change is written to activity_log.
--
-- The customer never sees any of it. What the customer sees is "This
-- product is currently unavailable."; the matches are an OWNER
-- notification.
-- =====================================================================

CREATE TABLE shop_discovery_setting (
    tenant_id                  BIGINT PRIMARY KEY REFERENCES tenant(tenant_id),
    discovery_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    share_shop_name            BOOLEAN NOT NULL DEFAULT FALSE,
    share_phone                BOOLEAN NOT NULL DEFAULT FALSE,
    share_approximate_location BOOLEAN NOT NULL DEFAULT FALSE,
    share_availability         BOOLEAN NOT NULL DEFAULT FALSE,
    latitude                   DECIMAL(9,6),
    longitude                  DECIMAL(9,6),
    search_radius_km           INT NOT NULL DEFAULT 5,
    created_at                 TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    updated_at                 TIMESTAMP(3),
    updated_by                 BIGINT,
    CONSTRAINT ck_discovery_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_discovery_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_discovery_radius CHECK (search_radius_km BETWEEN 1 AND 100),
    -- Being discoverable requires a location to be discovered AT. Enforced
    -- in the database so no code path can enable sharing for a shop that
    -- has not placed itself on the map.
    CONSTRAINT ck_discovery_needs_location CHECK (
        discovery_enabled = FALSE OR (latitude IS NOT NULL AND longitude IS NOT NULL))
);
-- Only enabled rows are ever scanned by the search; keep that scan small.
CREATE INDEX idx_discovery_enabled ON shop_discovery_setting (discovery_enabled) WHERE discovery_enabled = TRUE;

-- ---------------------------------------------------------------------
-- What a discovery search found for one product request, snapshotted
-- with exactly what the source shop permitted at that moment. A shop
-- that opts out later stops appearing in FUTURE results (spec: "must
-- immediately stop appearing in future discovery results"); a match it
-- already consented to stays on the requesting owner's record.
-- ---------------------------------------------------------------------
CREATE TABLE product_request_discovery_match (
    product_request_discovery_match_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_request_id   BIGINT NOT NULL REFERENCES product_request(product_request_id),
    -- The shop that has the product. Never exposed to the frontend as an
    -- id - it is here so a later "opted out, purge" or an audit can find
    -- the row, and so the same shop is not listed twice for one request.
    source_tenant_id     BIGINT NOT NULL REFERENCES tenant(tenant_id),
    matched_product_name VARCHAR(255) NOT NULL,
    availability         VARCHAR(20) NOT NULL,
    distance_km          DECIMAL(6,2),
    shop_name            VARCHAR(200),
    phone                VARCHAR(15),
    created_at           TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_discovery_match_availability CHECK (availability IN ('AVAILABLE', 'LIKELY_AVAILABLE')),
    CONSTRAINT uk_discovery_match UNIQUE (product_request_id, source_tenant_id)
);
CREATE INDEX idx_discovery_match_request ON product_request_discovery_match (product_request_id);

-- ---------------------------------------------------------------------
-- In-app notifications for the shop's own people. Generic on purpose -
-- CR-092's daily summary and low-stock alerts use the same row shape -
-- but the first writer is discovery.
-- ---------------------------------------------------------------------
CREATE TABLE owner_notification (
    owner_notification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             BIGINT NOT NULL REFERENCES tenant(tenant_id),
    notification_type     VARCHAR(30) NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    body                  VARCHAR(1000) NOT NULL,
    reference_type        VARCHAR(30),
    reference_id          BIGINT,
    read_at               TIMESTAMP(3),
    created_at            TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_owner_notification_type CHECK (
        notification_type IN ('PRODUCT_DISCOVERY', 'DAILY_SUMMARY', 'LOW_STOCK', 'SYSTEM'))
);
CREATE INDEX idx_owner_notification_tenant ON owner_notification (tenant_id, read_at, created_at);
