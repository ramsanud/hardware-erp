-- =====================================================================
-- CR-049 : Manual quotation-level discount, on top of the per-line
-- discounts CR-047 added.
--
-- A hardware shop negotiates twice: line by line ("₹50 off the locks"),
-- and then once more on the whole quote ("call it ₹2,800 and we have a
-- deal"). CR-047 covered the first. This covers the second.
--
-- REUSING discount_paise rather than adding a fourth column: V16 created
-- quotation.discount_paise BIGINT NOT NULL DEFAULT 0 for the coupon
-- feature, the Quotation entity never mapped it, and every row in it is
-- still 0. It is already named for exactly this value, so it becomes the
-- authoritative computed amount and only the two INTENT columns are new -
-- the same three-field shape as quotation_item, so both levels read the
-- same way:
--
--   discount_type     NONE | PERCENTAGE | AMOUNT
--   discount_percent  only meaningful when PERCENTAGE
--   discount_paise    ALWAYS the authoritative money figure (existing)
--
-- ORDER OF OPERATIONS - the part that must not be got wrong. The
-- quotation discount applies to the subtotal AFTER per-line discounts,
-- never to the original gross. Applying both to the same base would
-- double-count them; see the worked example in QuotationServiceImpl.
--
-- HISTORICAL DATA: existing rows already hold discount_paise = 0 and now
-- default to type NONE with 0%, which is arithmetically identical to the
-- behaviour before this migration. No stored total moves.
-- =====================================================================

ALTER TABLE quotation
    ADD COLUMN discount_type    VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    ADD COLUMN discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0;

ALTER TABLE quotation
    ADD CONSTRAINT ck_quotation_discount_type
        CHECK (discount_type IN ('NONE', 'PERCENTAGE', 'AMOUNT')),
    ADD CONSTRAINT ck_quotation_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100),
    ADD CONSTRAINT ck_quotation_discount_paise
        CHECK (discount_paise >= 0),
    -- A quotation may be discounted to zero, but never below it.
    ADD CONSTRAINT ck_quotation_subtotal_not_negative
        CHECK (subtotal_paise >= 0);
