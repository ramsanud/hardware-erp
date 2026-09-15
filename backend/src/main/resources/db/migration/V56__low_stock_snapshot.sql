-- =====================================================================
-- CR-084 : a daily record of how many products were low on stock.
--
-- The dashboard's "Low Stock Alerts" card wants a sparkline and a
-- "vs last week" delta, and the count is the one dashboard figure with no
-- history anywhere: stock_movement records quantities, not the moment a
-- product crossed its reorder level, and there is no way to reconstruct
-- "how many were low on a Tuesday" from what is stored.
--
-- One row per tenant per calendar day, written by a scheduled job just
-- after midnight and, on a shop's first read of the trend, taken lazily
-- for today so a fresh deploy shows a point on day one rather than a
-- fortnight of nothing. The count is the same predicate the Stock list's
-- "low stock only" filter and the reminder job use (quantity_on_hand <=
-- reorder_level), so the three never disagree.
--
-- taken_on is a DATE, not a timestamp: this is a calendar fact, and the
-- unique constraint is what makes the upsert safe to re-run.
-- =====================================================================

CREATE TABLE low_stock_snapshot (
    low_stock_snapshot_id BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL REFERENCES tenant (tenant_id),
    taken_on              DATE         NOT NULL,
    low_stock_count       INTEGER      NOT NULL CHECK (low_stock_count >= 0),
    created_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_low_stock_snapshot_tenant_day UNIQUE (tenant_id, taken_on)
);

-- The only read is "this tenant, this date range, in order".
CREATE INDEX idx_low_stock_snapshot_tenant_day ON low_stock_snapshot (tenant_id, taken_on);
