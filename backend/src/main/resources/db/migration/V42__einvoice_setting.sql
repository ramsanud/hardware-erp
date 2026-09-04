-- CR-053 backlog item 4: e-Invoice (IRN) data-collection UI shell. One
-- toggle, same shape as every other Settings boolean this session. No IRN/
-- acknowledgement columns are added anywhere - there is nothing to store
-- until a real GSP/NIC integration exists (no such credential is
-- configured in this environment), and empty placeholder columns that can
-- never be populated are worse than no columns at all.

ALTER TABLE tenant
    ADD COLUMN einvoice_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tenant.einvoice_enabled IS
    'Shows the e-Invoice (IRN) review section on the Invoice detail page. Generation itself always stays disabled - see InvoiceDetailPage''s own e-Invoice card for the honest "needs a GSP/NIC account" message.';
