-- =====================================================================
-- DEVELOPMENT / TEST SEED DATA  --  NEVER LOADED IN PRODUCTION
-- Engine: PostgreSQL 14+ (CR-014)
--
-- This file lives in classpath:db/seed, which is included in the flyway
-- locations of the dev and test profiles only. application-prod.yml lists
-- classpath:db/migration alone, so production physically cannot load it.
-- See CR-009.
--
-- Every password below is public knowledge. That is acceptable precisely
-- because these rows never reach a real shop.
--
--   Owner       9876543210 / owner@sarahardware.in       Owner@2026
--   Manager     9840112233 / manager@sarahardware.in     Manager@2026
--   Accountant  9840223344 / accounts@sarahardware.in    Account@2026
--   Staff (x7)  see below                                Staff@2026
--
-- Hashes are real BCrypt strength 12, so these accounts genuinely sign in.
-- =====================================================================

-- tenant_id = 1: the default tenant seeded by V6. There is only one tenant
-- until CR-016's follow-up (a real provisioning flow) lands.
INSERT INTO app_user
    (tenant_id, role_id, employee_code, full_name, mobile_no, email, password_hash,
     status, must_change_password, token_version, failed_login_attempts,
     last_login_at, password_changed_at, created_at, version)
VALUES
-- OWNER
(1, (SELECT role_id FROM role WHERE role_code = 'OWNER' AND tenant_id = 1),
 'EMP001', 'Saravanan Murugan', '9876543210', 'owner@sarahardware.in',
 '$2a$12$4VhkOYLa.GjwrAv9AQG6auuvibWPMhlR44p9QwqJUwV9viKE6y0zG',
 'ACTIVE', FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Second owner: lets the last-active-owner rule be exercised properly.
(1, (SELECT role_id FROM role WHERE role_code = 'OWNER' AND tenant_id = 1),
 'EMP002', 'Lakshmi Saravanan', '9876501234', 'lakshmi@sarahardware.in',
 '$2a$12$4VhkOYLa.GjwrAv9AQG6auuvibWPMhlR44p9QwqJUwV9viKE6y0zG',
 'ACTIVE', FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- MANAGER
(1, (SELECT role_id FROM role WHERE role_code = 'MANAGER' AND tenant_id = 1),
 'EMP003', 'Prakash Venkatesan', '9840112233', 'manager@sarahardware.in',
 '$2a$12$aXtx8zpNRx.80p7Jj8nObOTvXoleH3NZisXNn.MVbO7X/dUWO.KpS',
 'ACTIVE', FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- ACCOUNTANT
(1, (SELECT role_id FROM role WHERE role_code = 'ACCOUNTANT' AND tenant_id = 1),
 'EMP004', 'Meena Rajendran', '9840223344', 'accounts@sarahardware.in',
 '$2a$12$11GhpN5KLus8ABM0plsDKu1ZJkoekKMaCcF9oyVdYtaMTP64NouSy',
 'ACTIVE', FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- STAFF: counter and godown
(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP005', 'Karthik Raja', '9843012345', 'karthik@sarahardware.in',
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'ACTIVE', FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP006', 'Dinesh Kumar', '9843023456', 'dinesh@sarahardware.in',
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'ACTIVE', FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP007', 'Suresh Babu', '9843034567', 'suresh@sarahardware.in',
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'ACTIVE', FALSE, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Forced password change: exercises the must-change-password gate.
(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP008', 'Anitha Selvam', '9843045678', 'anitha@sarahardware.in',
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'ACTIVE', TRUE, 0, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Inactive: must be refused at login with the generic message.
(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP009', 'Ramesh Pandian', '9843056789', 'ramesh@sarahardware.in',
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'INACTIVE', FALSE, 0, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Suspended: same refusal, different reason in the audit log.
(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP010', 'Vignesh Arumugam', '9843067890', 'vignesh@sarahardware.in',
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'SUSPENDED', FALSE, 0, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- No email: proves forgot-password degrades quietly instead of failing.
(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP011', 'Bhaskar Nadar', '9843078901', NULL,
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'ACTIVE', FALSE, 0, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- Soft deleted: must not appear in any list and must not sign in.
(1, (SELECT role_id FROM role WHERE role_code = 'STAFF' AND tenant_id = 1),
 'EMP012', 'Former Employee', '9843089012', 'former@sarahardware.in',
 '$2a$12$S5QGMhNxU9FGGGibyuoJ8OHJu013ecosjHax9CDBDTnYTYaifmOKK',
 'INACTIVE', FALSE, 0, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

UPDATE app_user SET deleted_at = CURRENT_TIMESTAMP, deleted_by = 1 WHERE employee_code = 'EMP012';

-- ---------------------------------------------------------------------
-- A custom (non-system) role, so role edit and delete can be tested
-- without touching the four seeded system roles.
-- ---------------------------------------------------------------------
INSERT INTO role (tenant_id, role_code, role_name, description, system_role, status, created_at, version)
VALUES (1, 'STOCK_CLERK', 'Stock Clerk',
        'Godown staff: receives stock and adjusts inventory, cannot bill',
        FALSE, 'ACTIVE', CURRENT_TIMESTAMP, 0);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r JOIN permission p ON p.permission_code IN (
    'PRODUCT_VIEW', 'PRODUCT_VIEW_STOCK',
    'INVENTORY_VIEW', 'INVENTORY_ADJUST',
    'PURCHASE_VIEW')
WHERE r.role_code = 'STOCK_CLERK';

-- ---------------------------------------------------------------------
-- Security audit rows so the audit screen and its filters have data.
-- ---------------------------------------------------------------------
INSERT INTO security_audit_log
    (action, entity_type, entity_id, user_id, full_name, success,
     failure_reason, ip_address, user_agent, request_id, created_at)
VALUES
 ('LOGIN_SUCCESS','USER',1,1,'Saravanan Murugan',TRUE,NULL,'192.168.1.10',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000001',
  CURRENT_TIMESTAMP - INTERVAL '6 days'),
 ('LOGIN_FAILURE','USER',5,5,'Karthik Raja',FALSE,'Wrong password, attempt 1','192.168.1.22',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000002',
  CURRENT_TIMESTAMP - INTERVAL '5 days'),
 ('LOGIN_FAILURE','USER',5,5,'Karthik Raja',FALSE,'Wrong password, attempt 2','192.168.1.22',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000003',
  CURRENT_TIMESTAMP - INTERVAL '5 days'),
 ('LOGIN_SUCCESS','USER',5,5,'Karthik Raja',TRUE,NULL,'192.168.1.22',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000004',
  CURRENT_TIMESTAMP - INTERVAL '5 days'),
 ('USER_CREATED','USER',8,1,'Saravanan Murugan',TRUE,NULL,'192.168.1.10',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000005',
  CURRENT_TIMESTAMP - INTERVAL '4 days'),
 ('ROLE_CHANGED','USER',3,1,'Saravanan Murugan',TRUE,NULL,'192.168.1.10',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000006',
  CURRENT_TIMESTAMP - INTERVAL '3 days'),
 ('PASSWORD_CHANGED','USER',3,3,'Prakash Venkatesan',TRUE,NULL,'192.168.1.31',
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)','8f2a1c3d-1111-4a2b-9c3d-000000000007',
  CURRENT_TIMESTAMP - INTERVAL '3 days'),
 ('PASSWORD_RESET_REQUESTED','USER',4,4,'Meena Rajendran',TRUE,NULL,'192.168.1.44',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000008',
  CURRENT_TIMESTAMP - INTERVAL '2 days'),
 ('ACCOUNT_LOCKED','USER',10,10,'Vignesh Arumugam',FALSE,'Locked for 15 minutes','203.0.113.55',
  'curl/8.4.0','8f2a1c3d-1111-4a2b-9c3d-000000000009',
  CURRENT_TIMESTAMP - INTERVAL '2 days'),
 ('REFRESH_TOKEN_REUSE_DETECTED','USER',6,6,'Dinesh Kumar',FALSE,'All sessions revoked','203.0.113.77',
  'python-requests/2.31.0','8f2a1c3d-1111-4a2b-9c3d-000000000010',
  CURRENT_TIMESTAMP - INTERVAL '1 days'),
 ('LOGOUT','REFRESH_TOKEN',1,1,'Saravanan Murugan',TRUE,NULL,'192.168.1.10',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)','8f2a1c3d-1111-4a2b-9c3d-000000000011',
  CURRENT_TIMESTAMP - INTERVAL '1 days'),
 ('LOGOUT_ALL','USER',6,6,'Dinesh Kumar',TRUE,NULL,'192.168.1.66',
  'Mozilla/5.0 (Linux; Android 14)','8f2a1c3d-1111-4a2b-9c3d-000000000012',
  CURRENT_TIMESTAMP);
