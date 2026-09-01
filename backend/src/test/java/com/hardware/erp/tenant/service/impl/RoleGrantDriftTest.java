package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.auth.entity.PermissionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the second source of truth for default role grants.
 *
 * The migrations grant permissions to the role rows that exist when they
 * run; TenantRegistrationServiceImpl.ROLE_PERMISSIONS grants them to every
 * shop registered afterwards. Those two drifted apart once already - V25
 * added LABOUR_VIEW/LABOUR_MANAGE and granted them in SQL, but the map was
 * never updated, so every newly registered shop got MANAGER and ACCOUNTANT
 * roles with no labour access at all (BUG-LAB-006). OWNER hid the problem
 * by being assigned from the live permission table instead of the map.
 *
 * This test makes that failure mode impossible to reintroduce silently: a
 * new constant in PermissionCode must be either granted to a role here or
 * named in WITHHELD below, which forces the decision to be made and
 * written down rather than forgotten.
 */
class RoleGrantDriftTest {

    /**
     * Permissions each non-OWNER default role deliberately does NOT get.
     * Adding a code here is a decision, not a workaround - say why.
     */
    private static final Set<String> WITHHELD_FROM_MANAGER = Set.of(
            // Only the owner administers roles, users' passwords and the audit log.
            PermissionCode.USER_MANAGE,
            PermissionCode.ROLE_MANAGE,
            PermissionCode.AUDIT_VIEW,
            // Voiding a posted invoice and viewing supplier bank details are
            // owner-level financial actions.
            PermissionCode.INVOICE_CANCEL,
            PermissionCode.SUPPLIER_VIEW_BANK_ACCOUNT,
            PermissionCode.REPORT_FINANCIAL,
            PermissionCode.SETTINGS_MANAGE,
            // Developer diagnostics are not an ERP capability (CR-045). No
            // default role holds this - OWNER included, which is why
            // TenantRegistrationServiceImpl filters the DEVELOPER module out
            // of its otherwise-everything OWNER grant.
            PermissionCode.DEVELOPER_INSPECT);

    private static final Set<String> WITHHELD_FROM_ACCOUNTANT = Set.of(
            PermissionCode.USER_VIEW,
            PermissionCode.USER_MANAGE,
            PermissionCode.ROLE_VIEW,
            PermissionCode.ROLE_MANAGE,
            PermissionCode.AUDIT_VIEW,
            // An accountant records money; it does not maintain the catalogue,
            // stock levels, suppliers or projects.
            PermissionCode.PRODUCT_MANAGE,
            PermissionCode.SUPPLIER_MANAGE,
            PermissionCode.PURCHASE_MANAGE,
            PermissionCode.INVENTORY_ADJUST,
            PermissionCode.PROJECT_MANAGE,
            PermissionCode.PROJECT_MATERIAL_MANAGE,
            PermissionCode.QUOTATION_MANAGE,
            PermissionCode.INVOICE_CANCEL,
            PermissionCode.INVOICE_DISCOUNT_OVERRIDE,
            PermissionCode.SUPPLIER_VIEW_BANK_ACCOUNT,
            PermissionCode.COUPON_MANAGE,
            PermissionCode.SETTINGS_VIEW,
            PermissionCode.SETTINGS_MANAGE,
            // An accountant sees orders/challans for billing context but does
            // not raise them - same reasoning as QUOTATION_MANAGE above.
            PermissionCode.SALES_ORDER_MANAGE,
            PermissionCode.DELIVERY_CHALLAN_MANAGE,
            // See WITHHELD_FROM_MANAGER - developer diagnostics, CR-045.
            PermissionCode.DEVELOPER_INSPECT);

    private static final Set<String> WITHHELD_FROM_STAFF = Set.of(
            PermissionCode.USER_VIEW,
            PermissionCode.USER_MANAGE,
            PermissionCode.ROLE_VIEW,
            PermissionCode.ROLE_MANAGE,
            PermissionCode.AUDIT_VIEW,
            // Counter staff must never see purchase cost or margin - enforced
            // server-side, not by hiding a column in React.
            PermissionCode.PRODUCT_VIEW_COST,
            PermissionCode.PRODUCT_MANAGE,
            PermissionCode.SUPPLIER_VIEW,
            PermissionCode.SUPPLIER_MANAGE,
            PermissionCode.SUPPLIER_VIEW_BANK_ACCOUNT,
            PermissionCode.PURCHASE_VIEW,
            PermissionCode.PURCHASE_MANAGE,
            PermissionCode.INVENTORY_ADJUST,
            PermissionCode.PAYMENT_MANAGE,
            PermissionCode.EXPENSE_VIEW,
            PermissionCode.EXPENSE_MANAGE,
            PermissionCode.INVOICE_CANCEL,
            PermissionCode.INVOICE_DISCOUNT_OVERRIDE,
            PermissionCode.REPORT_VIEW,
            PermissionCode.REPORT_FINANCIAL,
            PermissionCode.SETTINGS_VIEW,
            PermissionCode.SETTINGS_MANAGE,
            PermissionCode.COUPON_MANAGE,
            PermissionCode.PROJECT_MANAGE,
            PermissionCode.PROJECT_MATERIAL_VIEW,
            PermissionCode.PROJECT_MATERIAL_MANAGE,
            // A worker's daily rate is cost-like data - same reasoning as
            // PRODUCT_VIEW_COST above.
            PermissionCode.LABOUR_VIEW,
            PermissionCode.LABOUR_MANAGE,
            // Dispatching goods and issuing a return/credit are not
            // front-counter authority - same footing as INVOICE_CANCEL.
            PermissionCode.DELIVERY_CHALLAN_VIEW,
            PermissionCode.DELIVERY_CHALLAN_MANAGE,
            PermissionCode.CREDIT_NOTE_VIEW,
            PermissionCode.CREDIT_NOTE_MANAGE,
            // See WITHHELD_FROM_MANAGER - developer diagnostics, CR-045.
            PermissionCode.DEVELOPER_INSPECT);

    /** Every constant declared in PermissionCode, read reflectively so a new one is picked up automatically. */
    private static Set<String> allPermissionCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (Field field : PermissionCode.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    codes.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError("PermissionCode." + field.getName() + " is not readable", e);
                }
            }
        }
        return codes;
    }

    private void assertEveryCodeIsDecided(String roleCode, Set<String> withheld) {
        Set<String> granted = TenantRegistrationServiceImpl.ROLE_PERMISSIONS.get(roleCode);
        assertThat(granted)
                .as("ROLE_PERMISSIONS has no entry for %s", roleCode)
                .isNotNull();

        Set<String> undecided = new TreeSet<>(allPermissionCodes());
        undecided.removeAll(granted);
        undecided.removeAll(withheld);

        assertThat(undecided)
                .as("""
                    %s is neither granted nor explicitly withheld these permission codes.
                    A new permission was added to PermissionCode without deciding whether \
                    each default role gets it - the exact drift that caused BUG-LAB-006. \
                    Add each code to the role's grant list in TenantRegistrationServiceImpl \
                    (and to a migration, for shops that already exist), or to this test's \
                    WITHHELD_FROM_%s set with a comment saying why.""",
                    roleCode, roleCode)
                .isEmpty();
    }

    @Test
    @DisplayName("every permission code is deliberately granted to or withheld from MANAGER")
    void managerGrantsAreComplete() {
        assertEveryCodeIsDecided("MANAGER", WITHHELD_FROM_MANAGER);
    }

    @Test
    @DisplayName("every permission code is deliberately granted to or withheld from ACCOUNTANT")
    void accountantGrantsAreComplete() {
        assertEveryCodeIsDecided("ACCOUNTANT", WITHHELD_FROM_ACCOUNTANT);
    }

    @Test
    @DisplayName("every permission code is deliberately granted to or withheld from STAFF")
    void staffGrantsAreComplete() {
        assertEveryCodeIsDecided("STAFF", WITHHELD_FROM_STAFF);
    }

    /** A grant list must never name a code that no longer exists, or the role silently loses it. */
    @Test
    void noRoleGrantsAnUnknownPermissionCode() {
        Set<String> known = allPermissionCodes();
        TenantRegistrationServiceImpl.ROLE_PERMISSIONS.forEach((roleCode, granted) -> {
            Set<String> unknown = new TreeSet<>(granted);
            unknown.removeAll(known);
            assertThat(unknown)
                    .as("%s is granted permission codes that do not exist in PermissionCode", roleCode)
                    .isEmpty();
        });
    }

    /** STAFF must never see cost or margin - the rule V1's seed comment calls out explicitly. */
    @Test
    void staffNeverSeesCostOrMargin() {
        assertThat(TenantRegistrationServiceImpl.ROLE_PERMISSIONS.get("STAFF"))
                .doesNotContain(PermissionCode.PRODUCT_VIEW_COST)
                .doesNotContain(PermissionCode.REPORT_FINANCIAL)
                .doesNotContain(PermissionCode.LABOUR_VIEW);
    }
}
