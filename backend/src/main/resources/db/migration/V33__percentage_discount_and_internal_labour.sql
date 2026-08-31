-- =====================================================================
-- CR-050 : Discount becomes percentage-only, and lines gain an internal
-- labour adjustment.
--
-- ---------------------------------------------------------------------
-- PART 1 - AMOUNT discounts converted to the equivalent percentage
-- ---------------------------------------------------------------------
-- CR-047/CR-049 allowed a discount to be entered as either a percentage
-- or a fixed rupee amount. The business has since settled on percentage
-- only, so every stored AMOUNT row is converted to the percentage that
-- produces the same money.
--
-- NO PRICE MOVES. discount_amount_paise, line_subtotal_paise,
-- line_gst_paise and line_total_paise are all left exactly as they are -
-- only discount_type and discount_percent are rewritten. The percentage
-- is derived from the figures already stored:
--
--     percent = discount_amount / (quantity x unit_price) x 100
--
-- rounded to the column's 2dp. That rounding cannot change any total,
-- because the authoritative money column is untouched; it only affects
-- what the line reports its intent as.
--
-- A zero-gross line (quantity or price of 0) would divide by zero, so it
-- is guarded and becomes NONE - a discount off nothing is nothing.
--
-- ---------------------------------------------------------------------
-- PART 2 - internal labour adjustment
-- ---------------------------------------------------------------------
-- An owner sometimes folds a handling/labour margin into the price
-- rather than billing it as a line. This is INTERNAL: the customer sees
-- the resulting rate and never a "labour" line.
--
-- Stored per line, not per document, because the owner turns it on for
-- some products and not others.
--
--     labour_percent       the owner's intent
--     labour_amount_paise  the computed money, authoritative
--
-- Applied AFTER the discount, per CR-050's agreed order:
--
--     gross          quantity x unit price
--     - discount     percentage of gross
--     = after disc.
--     + labour       percentage of the DISCOUNTED value
--     = net          <- line_subtotal_paise, the taxable amount
--     + GST          on net
--     = line total
--
-- Existing rows default to 0%, which is arithmetically identical to the
-- behaviour before this migration. No stored total moves.
-- =====================================================================

-- ---- Part 1: invoice_item ----
UPDATE invoice_item
SET discount_percent = LEAST(100, ROUND(
        (discount_amount_paise::numeric * 100)
        / NULLIF(ROUND(quantity * unit_price_paise), 0), 2)),
    discount_type = 'PERCENTAGE'
WHERE discount_type = 'AMOUNT'
  AND ROUND(quantity * unit_price_paise) > 0;

UPDATE invoice_item
SET discount_type = 'NONE', discount_percent = 0
WHERE discount_type = 'AMOUNT';

-- ---- Part 1: quotation_item ----
UPDATE quotation_item
SET discount_percent = LEAST(100, ROUND(
        (discount_amount_paise::numeric * 100)
        / NULLIF(ROUND(quantity * unit_price_paise), 0), 2)),
    discount_type = 'PERCENTAGE'
WHERE discount_type = 'AMOUNT'
  AND ROUND(quantity * unit_price_paise) > 0;

UPDATE quotation_item
SET discount_type = 'NONE', discount_percent = 0
WHERE discount_type = 'AMOUNT';

-- ---- Part 1: quotation header (CR-049 whole-quotation discount) ----
-- subtotal_paise is already NET of this discount, so the base it was
-- taken from is subtotal + discount.
UPDATE quotation
SET discount_percent = LEAST(100, ROUND(
        (discount_paise::numeric * 100)
        / NULLIF(subtotal_paise + discount_paise, 0), 2)),
    discount_type = 'PERCENTAGE'
WHERE discount_type = 'AMOUNT'
  AND (subtotal_paise + discount_paise) > 0;

UPDATE quotation
SET discount_type = 'NONE', discount_percent = 0
WHERE discount_type = 'AMOUNT';

-- ---- Part 1: retire AMOUNT from the CHECK constraints ----
ALTER TABLE invoice_item DROP CONSTRAINT ck_invoice_item_discount_type;
ALTER TABLE invoice_item ADD CONSTRAINT ck_invoice_item_discount_type
    CHECK (discount_type IN ('NONE', 'PERCENTAGE'));

ALTER TABLE quotation_item DROP CONSTRAINT ck_quotation_item_discount_type;
ALTER TABLE quotation_item ADD CONSTRAINT ck_quotation_item_discount_type
    CHECK (discount_type IN ('NONE', 'PERCENTAGE'));

ALTER TABLE quotation DROP CONSTRAINT ck_quotation_discount_type;
ALTER TABLE quotation ADD CONSTRAINT ck_quotation_discount_type
    CHECK (discount_type IN ('NONE', 'PERCENTAGE'));

-- ---- Part 2: internal labour ----
ALTER TABLE invoice_item
    ADD COLUMN labour_percent      DECIMAL(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN labour_amount_paise BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE invoice_item
    ADD CONSTRAINT ck_invoice_item_labour_percent
        CHECK (labour_percent >= 0 AND labour_percent <= 100),
    ADD CONSTRAINT ck_invoice_item_labour_amount
        CHECK (labour_amount_paise >= 0);

ALTER TABLE quotation_item
    ADD COLUMN labour_percent      DECIMAL(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN labour_amount_paise BIGINT       NOT NULL DEFAULT 0;

ALTER TABLE quotation_item
    ADD CONSTRAINT ck_quotation_item_labour_percent
        CHECK (labour_percent >= 0 AND labour_percent <= 100),
    ADD CONSTRAINT ck_quotation_item_labour_amount
        CHECK (labour_amount_paise >= 0);
