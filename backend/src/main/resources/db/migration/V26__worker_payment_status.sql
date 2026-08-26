-- =====================================================================
-- CR-037 follow-up : a worker payment had no way to be voided.
-- A ₹5,000 typed where ₹500 was meant was permanently baked into the
-- worker's paid total with no in-app correction - the row could only be
-- fixed by editing the database by hand.
--
-- Same shape as business_expense (CR-036 phase 3): a status column and a
-- soft cancel, never a hard DELETE. A payment is a financial record - it
-- stays visible in history, marked CANCELLED, and is excluded from the
-- paid/balance figures rather than vanishing from them.
-- =====================================================================

ALTER TABLE worker_payment
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE worker_payment
    ADD CONSTRAINT ck_worker_payment_status CHECK (status IN ('ACTIVE', 'CANCELLED'));
