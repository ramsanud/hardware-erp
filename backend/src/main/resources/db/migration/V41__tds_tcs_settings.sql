-- CR-053 backlog item 3: TDS/TCS settings. Same shape as V40's Additional
-- Settings toggles - shop-wide preferences on tenant, all default
-- false/zero so an existing tenant is unaffected until the owner opens
-- Settings. Deliberately informational only in this first cut: the
-- computed TDS/TCS figure is shown on the Purchase/Invoice detail page and
-- PDF, but never subtracted from or added to the stored total_paise/
-- balance_paise on either document - see CHANGE_REQUEST_REGISTRY.md's
-- CR-053 backlog item 3 entry for why folding a statutory tax
-- calculation into core financial totals needs its own, separately
-- reviewed change, not a drive-by addition alongside six other features.

ALTER TABLE tenant
    ADD COLUMN tds_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN tds_section_code VARCHAR(20),
    ADD COLUMN tds_rate_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN tcs_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN tcs_section_code VARCHAR(20),
    ADD COLUMN tcs_rate_percent DECIMAL(5,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN tenant.tds_section_code IS
    'Free text, e.g. 194Q - not validated against an Income Tax Act lookup table, which does not exist in this schema.';
COMMENT ON COLUMN tenant.tcs_section_code IS
    'Free text, e.g. 206C(1H) - same convention as tds_section_code.';
