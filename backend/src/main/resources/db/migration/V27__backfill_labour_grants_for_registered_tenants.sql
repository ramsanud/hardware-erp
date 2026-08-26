-- =====================================================================
-- BUG-LAB-006 (CR-037) : shops registered through /v1/tenants/register
-- after V25 shipped got MANAGER and ACCOUNTANT roles with NO labour
-- permissions at all.
--
-- Two independent sources of truth for the default role grants had
-- silently drifted: V25 granted LABOUR_VIEW/LABOUR_MANAGE by UPDATEing
-- the role rows that existed at migration time, while every NEW shop
-- builds its roles from a hardcoded map in
-- TenantRegistrationServiceImpl - which was never updated. OWNER was
-- unaffected because that service assigns OWNER from the live permission
-- table rather than from the map.
--
-- The map is now fixed in Java, and RoleGrantDriftTest fails the build if
-- a future permission code is added without deciding each default role's
-- access. This migration repairs the tenants already created with the gap.
--
-- Written as an anti-join so it is safe to re-run and cannot create a
-- duplicate role_permission row.
-- =====================================================================

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
CROSS JOIN permission p
WHERE r.role_code IN ('MANAGER', 'ACCOUNTANT')
  AND p.permission_code IN ('LABOUR_VIEW', 'LABOUR_MANAGE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permission existing
      WHERE existing.role_id = r.role_id
        AND existing.permission_id = p.permission_id
  );
