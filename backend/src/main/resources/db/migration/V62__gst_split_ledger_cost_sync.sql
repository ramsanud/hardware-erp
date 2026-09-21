-- =====================================================================
-- CR-091 : Critical business-logic completion, phases 1/3/4/6/9 of the
-- brief. Everything here is additive; no existing row's meaning changes
-- and no applied migration is touched.
--
--   Phase 1  GST split frozen on the invoice: supply type, place of
--            supply, CGST/SGST/IGST per line and per invoice.
--   Phase 3  Invoice cancellation carries who / when / why.
--   Phase 4  customer_ledger_entry - the authoritative receivables ledger.
--   Phase 6  Cost basis frozen on each sold line, weighted-average cost
--            tracked on stock.
--   Phase 9  sync_transaction - the server side of offline sync.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Phase 1: GST split. Historical rows are backfilled from THEIR OWN
-- frozen gst rate and the shop/customer state at the time of the
-- migration - the same rule InvoicePdfService has printed since Module 11
-- (half rounded down to CGST, remainder to SGST) and the place-of-supply
-- precedence CR-087's GSTR-1 uses (customer state, else GSTIN prefix,
-- else shop state). A later change to a product's GST rate or a
-- customer's state never rewrites these columns.
-- ---------------------------------------------------------------------
ALTER TABLE invoice
    ADD COLUMN supply_type VARCHAR(10),
    ADD COLUMN place_of_supply_state_code VARCHAR(2),
    ADD COLUMN cgst_paise BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN sgst_paise BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN igst_paise BIGINT NOT NULL DEFAULT 0;

ALTER TABLE invoice_item
    ADD COLUMN cgst_paise BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN sgst_paise BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN igst_paise BIGINT NOT NULL DEFAULT 0;

UPDATE invoice i
   SET place_of_supply_state_code = COALESCE(
           NULLIF(c.state_code, ''),
           NULLIF(substr(c.gst_no, 1, 2), ''),
           NULLIF(t.state_code, '')),
       supply_type = CASE
           WHEN NULLIF(t.state_code, '') IS NULL THEN 'INTRA'
           WHEN COALESCE(NULLIF(c.state_code, ''), NULLIF(substr(c.gst_no, 1, 2), '')) IS NULL THEN 'INTRA'
           WHEN t.state_code = COALESCE(NULLIF(c.state_code, ''), NULLIF(substr(c.gst_no, 1, 2), '')) THEN 'INTRA'
           ELSE 'INTER'
       END
  FROM customer c, tenant t
 WHERE c.customer_id = i.customer_id AND t.tenant_id = i.tenant_id;

UPDATE invoice_item ii
   SET cgst_paise = CASE WHEN i.supply_type = 'INTER' THEN 0 ELSE ii.line_gst_paise / 2 END,
       sgst_paise = CASE WHEN i.supply_type = 'INTER' THEN 0 ELSE ii.line_gst_paise - (ii.line_gst_paise / 2) END,
       igst_paise = CASE WHEN i.supply_type = 'INTER' THEN ii.line_gst_paise ELSE 0 END
  FROM invoice i
 WHERE i.invoice_id = ii.invoice_id;

UPDATE invoice i
   SET cgst_paise = COALESCE(s.cgst, 0),
       sgst_paise = COALESCE(s.sgst, 0),
       igst_paise = COALESCE(s.igst, 0)
  FROM (SELECT invoice_id, SUM(cgst_paise) AS cgst, SUM(sgst_paise) AS sgst, SUM(igst_paise) AS igst
          FROM invoice_item GROUP BY invoice_id) s
 WHERE s.invoice_id = i.invoice_id;

ALTER TABLE invoice
    ALTER COLUMN supply_type SET DEFAULT 'INTRA',
    ALTER COLUMN supply_type SET NOT NULL,
    ADD CONSTRAINT ck_invoice_supply_type CHECK (supply_type IN ('INTRA', 'INTER')),
    ADD CONSTRAINT ck_invoice_gst_split CHECK (cgst_paise >= 0 AND sgst_paise >= 0 AND igst_paise >= 0);

-- ---------------------------------------------------------------------
-- Phase 3: cancellation audit fields. Existing CANCELLED rows predate the
-- reason requirement; they keep a null reason and the activity_log row
-- they already wrote. New cancellations must supply one.
-- ---------------------------------------------------------------------
ALTER TABLE invoice
    ADD COLUMN cancelled_at TIMESTAMP(3),
    ADD COLUMN cancelled_by BIGINT,
    ADD COLUMN cancellation_reason VARCHAR(255);

-- ---------------------------------------------------------------------
-- Phase 6: cost basis. stock.average_cost_paise is the weighted-average
-- cost of what is on the shelf, moved only by PURCHASE_RECEIPT (and its
-- reversal). invoice_item.cost_price_paise is that average frozen at the
-- moment of sale - the number COGS is computed from, forever. Backfill:
-- the product's purchase price at migration time, the only cost history
-- the schema had before now (ReportDtos' own note says exactly this).
-- ---------------------------------------------------------------------
ALTER TABLE stock
    ADD COLUMN average_cost_paise BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_stock_average_cost CHECK (average_cost_paise >= 0);

UPDATE stock s SET average_cost_paise = p.purchase_price_paise
  FROM product p WHERE p.product_id = s.product_id;

ALTER TABLE invoice_item
    ADD COLUMN cost_price_paise BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_invoice_item_cost CHECK (cost_price_paise >= 0);

UPDATE invoice_item ii SET cost_price_paise = p.purchase_price_paise
  FROM product p WHERE p.product_id = ii.product_id;

-- ---------------------------------------------------------------------
-- Phase 4: the customer ledger. Append-only. Outstanding is Σ debit −
-- Σ credit over a customer's rows; nothing else is authoritative.
--
--   INVOICE               debit  invoice total
--   PAYMENT               credit amount
--   SALES_RETURN          credit credit-note total
--   INVOICE_CANCELLATION  credit invoice total (payments already taken
--                                stay as credit - the customer is now in
--                                advance, which the ledger shows honestly)
--   ADJUSTMENT            either, with a reason, PAYMENT_MANAGE only
-- ---------------------------------------------------------------------
CREATE TABLE customer_ledger_entry (
    customer_ledger_entry_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(tenant_id),
    customer_id     BIGINT NOT NULL REFERENCES customer(customer_id),
    entry_type      VARCHAR(30) NOT NULL,
    entry_date      TIMESTAMP(3) NOT NULL,
    debit_paise     BIGINT NOT NULL DEFAULT 0,
    credit_paise    BIGINT NOT NULL DEFAULT 0,
    reference_type  VARCHAR(30) NOT NULL,
    reference_id    BIGINT NOT NULL,
    reference_number VARCHAR(40),
    notes           VARCHAR(255),
    created_at      TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    created_by      BIGINT,
    CONSTRAINT ck_ledger_entry_type CHECK (
        entry_type IN ('INVOICE', 'PAYMENT', 'SALES_RETURN', 'INVOICE_CANCELLATION', 'ADJUSTMENT')),
    CONSTRAINT ck_ledger_amounts CHECK (debit_paise >= 0 AND credit_paise >= 0
        AND (debit_paise > 0 OR credit_paise > 0)),
    -- Exactly one ledger row per financial event: a payment applied twice
    -- (BUG class the brief's Phase 4 names) is a constraint violation,
    -- not a silent double credit.
    CONSTRAINT uk_ledger_event UNIQUE (tenant_id, entry_type, reference_type, reference_id)
);
CREATE INDEX idx_ledger_customer ON customer_ledger_entry (tenant_id, customer_id, entry_date);

-- Backfill from what already happened, in the order it happened.
INSERT INTO customer_ledger_entry (tenant_id, customer_id, entry_type, entry_date, debit_paise, credit_paise,
                                   reference_type, reference_id, reference_number, created_at, created_by)
SELECT i.tenant_id, i.customer_id, 'INVOICE', i.created_at, i.total_paise, 0,
       'INVOICE', i.invoice_id, i.invoice_number, i.created_at, i.created_by
  FROM invoice i;

INSERT INTO customer_ledger_entry (tenant_id, customer_id, entry_type, entry_date, debit_paise, credit_paise,
                                   reference_type, reference_id, reference_number, created_at, created_by)
SELECT p.tenant_id, i.customer_id, 'PAYMENT', p.payment_date, 0, p.amount_paise,
       'PAYMENT', p.payment_id, i.invoice_number, p.created_at, p.created_by
  FROM payment p JOIN invoice i ON i.invoice_id = p.invoice_id;

INSERT INTO customer_ledger_entry (tenant_id, customer_id, entry_type, entry_date, debit_paise, credit_paise,
                                   reference_type, reference_id, reference_number, created_at, created_by)
SELECT cn.tenant_id, cn.customer_id, 'SALES_RETURN', cn.created_at, 0, cn.total_paise,
       'CREDIT_NOTE', cn.credit_note_id, cn.credit_note_number, cn.created_at, cn.created_by
  FROM credit_note cn WHERE cn.status = 'ISSUED';

INSERT INTO customer_ledger_entry (tenant_id, customer_id, entry_type, entry_date, debit_paise, credit_paise,
                                   reference_type, reference_id, reference_number, created_at, created_by)
SELECT i.tenant_id, i.customer_id, 'INVOICE_CANCELLATION', COALESCE(i.updated_at, i.created_at), 0, i.total_paise,
       'INVOICE', i.invoice_id, i.invoice_number, COALESCE(i.updated_at, i.created_at), i.updated_by
  FROM invoice i WHERE i.status = 'CANCELLED';

-- ---------------------------------------------------------------------
-- Phase 9: offline sync. One row per client-generated transaction. The
-- (tenant, client_uuid) uniqueness is what makes a replay safe: the
-- second upload of the same UUID finds this row and returns its stored
-- outcome instead of creating a second invoice. Conflicts are recorded,
-- never silently resolved (brief: "do NOT silently overwrite server data").
-- ---------------------------------------------------------------------
CREATE TABLE sync_transaction (
    sync_transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenant(tenant_id),
    client_uuid        UUID NOT NULL,
    device_id          VARCHAR(100) NOT NULL,
    transaction_type   VARCHAR(30) NOT NULL,
    payload            JSONB NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_reference_type VARCHAR(30),
    result_reference_id   BIGINT,
    result_reference_number VARCHAR(40),
    conflict_reason    VARCHAR(500),
    client_created_at  TIMESTAMP(3) NOT NULL,
    received_at        TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    synced_at          TIMESTAMP(3),
    attempt_count      INT NOT NULL DEFAULT 1,
    created_by         BIGINT,
    CONSTRAINT uk_sync_transaction_client UNIQUE (tenant_id, client_uuid),
    CONSTRAINT ck_sync_transaction_type CHECK (transaction_type IN ('INVOICE')),
    CONSTRAINT ck_sync_transaction_status CHECK (status IN ('PENDING', 'SYNCED', 'FAILED', 'CONFLICT'))
);
CREATE INDEX idx_sync_transaction_tenant ON sync_transaction (tenant_id, status, received_at);
