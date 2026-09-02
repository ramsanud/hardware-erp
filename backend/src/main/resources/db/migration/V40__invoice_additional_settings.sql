-- CR-053 backlog item 1: Invoice "Additional Settings" toggles (myBillBook
-- parity). Same shape as V38's invoice_theme - shop-wide preferences on
-- tenant, read by InvoicePdfService. All default to FALSE/NULL so an
-- existing tenant's invoice PDF is byte-for-byte unchanged until the owner
-- opens Settings and turns one on.

ALTER TABLE tenant
    ADD COLUMN show_item_description BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN show_alternate_unit   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN show_price_history    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN enable_free_quantity  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN show_invoice_time     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN show_item_image       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN invoice_tagline       VARCHAR(255);

COMMENT ON COLUMN tenant.invoice_tagline IS
    'Free text printed under the shop name on the invoice PDF, e.g. a motto. Presence is the toggle - blank means nothing prints, same convention as signatory_name.';

-- A product's secondary unit of measure, e.g. "1 BOX = 12 PCS" - both
-- nullable together (a product with no alternate unit leaves both null, not
-- a zero conversion factor that would read as "12 PCS = 0 BOX").
ALTER TABLE product
    ADD COLUMN alt_unit_label              VARCHAR(30),
    ADD COLUMN alt_unit_conversion_factor  DECIMAL(18,4);

-- Free/bonus units given with a sale, over and above the billed quantity -
-- printed as "10 + 2 Free" and, critically, deducted from stock alongside
-- quantity (a shop giving 2 free units genuinely loses 12 units of
-- inventory, not 10) but never priced - LineDiscount's taxable-value math
-- is untouched by this column.
ALTER TABLE invoice_item
    ADD COLUMN free_quantity DECIMAL(18,4) NOT NULL DEFAULT 0;
