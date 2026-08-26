-- =====================================================================
-- Multi-page GST tax invoice with shipment + payment sections. Shop's
-- own PAN/contact/bank/UPI (needed to print them and to build the UPI
-- QR code), and per-invoice shipment details (transport mode, vehicle
-- number, delivery address) - all optional, a shop that hasn't filled
-- them in still trades, the PDF just omits that section.
-- =====================================================================

ALTER TABLE tenant
    ADD COLUMN pan_no             VARCHAR(10),
    ADD COLUMN phone               VARCHAR(15),
    ADD COLUMN email                VARCHAR(255),
    ADD COLUMN bank_account_name    VARCHAR(200),
    ADD COLUMN bank_account_no      VARCHAR(30),
    ADD COLUMN bank_ifsc             VARCHAR(11),
    ADD COLUMN bank_name              VARCHAR(200),
    ADD COLUMN upi_id                 VARCHAR(100);

ALTER TABLE invoice
    ADD COLUMN transport_mode    VARCHAR(50),
    ADD COLUMN vehicle_number     VARCHAR(20),
    ADD COLUMN delivery_address    VARCHAR(500);

-- unit (UQC - Unit Quantity Code, e.g. PCS/BOX/KG) snapshotted at sale
-- time, same reasoning as unit_price_paise/gst_rate_percent
-- (PROJECT_SKILLS #17) - a later change to product.unit must never alter
-- an already-issued invoice line. Backfilled from the product's current
-- unit for existing rows, since none were snapshotted before this
-- migration existed.
ALTER TABLE invoice_item ADD COLUMN unit VARCHAR(20);

UPDATE invoice_item ii
SET unit = p.unit
FROM product p
WHERE ii.product_id = p.product_id AND ii.unit IS NULL;

ALTER TABLE invoice_item ALTER COLUMN unit SET NOT NULL;

ALTER TABLE quotation_item ADD COLUMN unit VARCHAR(20);

UPDATE quotation_item qi
SET unit = p.unit
FROM product p
WHERE qi.product_id = p.product_id AND qi.unit IS NULL;

ALTER TABLE quotation_item ALTER COLUMN unit SET NOT NULL;
