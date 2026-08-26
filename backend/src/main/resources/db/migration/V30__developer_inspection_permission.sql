-- =====================================================================
-- CR-045 : developer inspection permission
--
-- The first permission in the catalogue that is not an ERP capability. It
-- grants access to /api/v1/dev/** - runtime diagnostics, not shop data.
--
-- Deliberately granted to NOBODY, including OWNER.
--
-- Every earlier module migration ends with an explicit OWNER grant, because
-- V1's `CROSS JOIN permission` ran once and does not retroactively pick up
-- later codes (see the note in V25). This migration breaks that pattern on
-- purpose: running a hardware shop and debugging the software that runs it
-- are different jobs. Granting this to OWNER by default would put a
-- diagnostics console one stolen owner password away in every shop, and
-- would make "admin" mean "developer" - exactly the conflation CR-045
-- exists to prevent.
--
-- The corresponding exclusion for shops registered AFTER this migration
-- lives in TenantRegistrationServiceImpl, which assigns OWNER from the live
-- permission table and therefore has to filter the DEVELOPER module out
-- explicitly. DeveloperInspectionModuleGrantTest asserts both halves.
--
-- To grant it, an owner assigns it to a role on the Roles screen, in the
-- environment where it is wanted. It still does nothing in production:
-- application-prod.yml sets app.developer-inspection.enabled to a hard
-- false and DeveloperInspectionService forces it off under the prod
-- profile regardless.
-- =====================================================================
INSERT INTO permission (permission_code, permission_name, description, module_code, display_order) VALUES
 ('DEVELOPER_INSPECT', 'Developer inspection',
  'Access developer diagnostics in non-production environments. Grants no access to shop data.',
  'DEVELOPER', 10);
