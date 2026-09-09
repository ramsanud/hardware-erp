-- =====================================================================
-- CR-067 : shop data reset permission.
--
-- Deliberately a code of its own rather than a use of SETTINGS_MANAGE.
-- Correcting the shop's GSTIN and erasing its entire trading history are
-- not the same authority, and folding the second into the first would
-- mean any role ever granted SETTINGS_MANAGE silently acquires a wipe
-- button.
--
-- Granted to OWNER only. V1's `CROSS JOIN permission` ran once and does
-- not retroactively pick up codes added later, so - exactly as V18 and
-- V25 had to - the grant for the roles that already exist is written out
-- here. Shops registered after this migration get it through
-- TenantRegistrationServiceImpl's "OWNER gets every permission except the
-- DEVELOPER module" rule, which needs no edit.
--
-- MANAGER, ACCOUNTANT and STAFF are omitted on purpose, and that decision
-- is pinned by RoleGrantDriftTest's WITHHELD_* sets rather than left to
-- whoever reads this file next.
-- =====================================================================
INSERT INTO permission (permission_code, permission_name, description, module_code, display_order) VALUES
 ('DATA_RESET', 'Reset shop data',
  'Permanently delete this shop''s transactional data - invoices, purchases, payments, expenses, projects, labour records and the stock ledger. Masters, users, roles and settings are kept.',
  'SETTINGS', 30);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r JOIN permission p ON p.permission_code = 'DATA_RESET'
WHERE r.role_code = 'OWNER';
