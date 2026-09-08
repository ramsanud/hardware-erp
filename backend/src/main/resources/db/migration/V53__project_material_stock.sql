-- ---------------------------------------------------------------------
-- CR-064 - project material consumption moves stock.
--
-- Two changes to stock_movement, one of which fixes a defect that predates
-- this CR.
--
-- 1. WIDEN movement_type.
--
--    The column has been VARCHAR(20) since V8, and its CHECK constraint has
--    been extended four times (V8, V21, V36, V37) without anyone widening
--    the column underneath it. V37 added 'SALES_RETURN_REVERSAL', which is
--    21 characters - a value the constraint permits and the column cannot
--    store. Cancelling a credit note would have failed on a Postgres
--    "value too long for type character varying(20)" error; it is latent
--    only because the Credit Note module has no frontend yet. See
--    BUG-DB-001.
--
--    30 leaves room for the longest value this file adds
--    ('PROJECT_CONSUMPTION_REVERSAL', 28) and is the reason the widening is
--    here rather than in a separate migration: the same fix is required for
--    both, and splitting it would leave one of them broken in between.
--
-- 2. Two new movement types, named after the existing pairs
--    (SALE/SALE_REVERSAL, DELIVERY/DELIVERY_REVERSAL). Stock leaves when a
--    project records what it actually used, and comes back only when that
--    record is corrected downwards or removed - never because a project was
--    cancelled, which asserts a physical return that did not happen. See
--    CR-064 for that decision and who made it.
-- ---------------------------------------------------------------------

ALTER TABLE stock_movement ALTER COLUMN movement_type TYPE VARCHAR(30);

ALTER TABLE stock_movement DROP CONSTRAINT ck_stock_movement_type;
ALTER TABLE stock_movement ADD CONSTRAINT ck_stock_movement_type CHECK (
    movement_type IN (
        'INITIAL', 'ADJUSTMENT', 'SALE', 'SALE_REVERSAL',
        'PURCHASE_RECEIPT', 'PURCHASE_RETURN',
        'DELIVERY', 'DELIVERY_REVERSAL',
        'SALES_RETURN', 'SALES_RETURN_REVERSAL',
        'PROJECT_CONSUMPTION', 'PROJECT_CONSUMPTION_REVERSAL'
    ));

-- Every project-material movement carries reference_type = 'PROJECT_MATERIAL'
-- and reference_id = project_material_id, so a material row's whole stock
-- history (consumption, corrections, removal) can be read back in one query.
-- The existing idx on (tenant_id, product_id) does not serve that lookup.
CREATE INDEX idx_stock_movement_reference
    ON stock_movement (tenant_id, reference_type, reference_id);
