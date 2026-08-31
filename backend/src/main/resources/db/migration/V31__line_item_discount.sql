-- =====================================================================
-- CR-047 : Manual per-line discount on quotation and invoice items.
--
-- Until now the only discount that could reach a line was a COUPON, applied
-- by InvoiceServiceImpl.applyCoupon() which reduces line_subtotal_paise in
-- place. A shop owner negotiating "₹50 off the door locks" at the counter had
-- no way to record it.
--
-- THREE COLUMNS, and the reason there are three rather than two:
--
--   discount_type          NONE | PERCENTAGE | AMOUNT. Records the owner's
--                          INTENT, which matters because a 10% line must
--                          still read as "10%" on the PDF and must carry the
--                          percentage - not a frozen rupee figure - when the
--                          quotation is converted into an invoice.
--
--   discount_percent       Only meaningful when type = PERCENTAGE. Kept so
--                          the intent above survives a round trip.
--
--   discount_amount_paise  ALWAYS the authoritative money figure, for both
--                          types: the entered amount when type = AMOUNT, and
--                          the computed amount when type = PERCENTAGE. Every
--                          total is built from this column, never from a
--                          percentage re-derived at read time, so a stored
--                          document can never re-price itself.
--
-- HISTORICAL DATA: every existing row gets type NONE, percent 0 and amount 0,
-- which is arithmetically identical to the behaviour before this migration -
-- gross becomes net, and line_subtotal_paise is unchanged. Nothing is
-- recomputed and no existing total moves.
--
-- line_subtotal_paise keeps its existing meaning: the NET taxable amount
-- after discount. That is already what applyCoupon() leaves behind, so the
-- coupon path and the manual path agree, and GST is charged on the
-- discounted value in both.
-- =====================================================================

ALTER TABLE invoice_item
    ADD COLUMN discount_type         VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    ADD COLUMN discount_percent      DECIMAL(5,2)  NOT NULL DEFAULT 0,
    ADD COLUMN discount_amount_paise BIGINT        NOT NULL DEFAULT 0;

ALTER TABLE invoice_item
    ADD CONSTRAINT ck_invoice_item_discount_type
        CHECK (discount_type IN ('NONE', 'PERCENTAGE', 'AMOUNT')),
    ADD CONSTRAINT ck_invoice_item_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100),
    ADD CONSTRAINT ck_invoice_item_discount_amount
        CHECK (discount_amount_paise >= 0),
    -- A discount may equal the gross (a 100% "free of charge" line) but never
    -- exceed it, which is what would drive a line total negative.
    ADD CONSTRAINT ck_invoice_item_discount_not_negative
        CHECK (line_subtotal_paise >= 0);

ALTER TABLE quotation_item
    ADD COLUMN discount_type         VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    ADD COLUMN discount_percent      DECIMAL(5,2)  NOT NULL DEFAULT 0,
    ADD COLUMN discount_amount_paise BIGINT        NOT NULL DEFAULT 0;

ALTER TABLE quotation_item
    ADD CONSTRAINT ck_quotation_item_discount_type
        CHECK (discount_type IN ('NONE', 'PERCENTAGE', 'AMOUNT')),
    ADD CONSTRAINT ck_quotation_item_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100),
    ADD CONSTRAINT ck_quotation_item_discount_amount
        CHECK (discount_amount_paise >= 0),
    ADD CONSTRAINT ck_quotation_item_discount_not_negative
        CHECK (line_subtotal_paise >= 0);
