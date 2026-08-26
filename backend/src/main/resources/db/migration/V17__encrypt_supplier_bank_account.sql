-- CR-018: supplier.bank_account_no moves from plaintext VARCHAR(30) to
-- AES-256-GCM ciphertext (application-level, via BankAccountNumberConverter -
-- the database only ever sees ciphertext, never the key). Widened to hold a
-- base64 ciphertext, not the raw account number.
--
-- Existing plaintext rows are NOT rewritten by this migration - encryption
-- needs the application's key, not the database's, so that step is a
-- one-time Java data migration (SupplierBankAccountEncryptionRunner) that
-- runs at application startup and is a no-op once every row is encrypted.
-- BankAccountNumberConverter tolerates un-prefixed legacy plaintext on read
-- in the meantime, so nothing breaks between this migration applying and
-- that runner completing.
ALTER TABLE supplier ALTER COLUMN bank_account_no TYPE VARCHAR(255);

-- ---------------------------------------------------------------------
-- New permission, gating GET /v1/suppliers/{id}/bank-account-number.
-- Deliberately separate from SUPPLIER_VIEW: every supplier viewer already
-- sees the masked last-4-digits; only a caller with this on top can reveal
-- the full number. Global catalog (permission is not tenant-scoped, see
-- CR-016); every existing tenant's OWNER is backfilled here, same pattern
-- as V16's coupon permissions.
-- ---------------------------------------------------------------------
INSERT INTO permission (permission_code, permission_name, description, module_code, display_order) VALUES
 ('SUPPLIER_VIEW_BANK_ACCOUNT', 'Reveal supplier bank account number',
  'View the full (unmasked) bank account number, beyond the last-4 shown to every SUPPLIER_VIEW holder',
  'SUPPLIER', 15);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM role r CROSS JOIN permission p
WHERE r.role_code = 'OWNER' AND p.permission_code = 'SUPPLIER_VIEW_BANK_ACCOUNT';
