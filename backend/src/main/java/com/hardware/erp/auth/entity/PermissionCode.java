package com.hardware.erp.auth.entity;

/**
 * Compile-time constants for the permissions that exist today.
 *
 * The database `permission` table is authoritative. This class exists only so
 * @PreAuthorize expressions are checked by the compiler instead of being
 * free-text strings that survive a typo. Adding a constant here is optional;
 * adding the row in a migration is mandatory.
 *
 * PermissionCodeConsistencyTest asserts every constant here exists in the
 * database, so the two can never silently diverge.
 */
public final class PermissionCode {

    private PermissionCode() {
    }

    // AUTH
    public static final String USER_VIEW   = "USER_VIEW";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String ROLE_VIEW   = "ROLE_VIEW";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";
    public static final String AUDIT_VIEW  = "AUDIT_VIEW";

    // CUSTOMER
    public static final String CUSTOMER_VIEW   = "CUSTOMER_VIEW";
    public static final String CUSTOMER_MANAGE = "CUSTOMER_MANAGE";

    // SUPPLIER
    public static final String SUPPLIER_VIEW   = "SUPPLIER_VIEW";
    public static final String SUPPLIER_MANAGE = "SUPPLIER_MANAGE";
    public static final String SUPPLIER_VIEW_BANK_ACCOUNT = "SUPPLIER_VIEW_BANK_ACCOUNT";

    // PRODUCT
    public static final String PRODUCT_VIEW       = "PRODUCT_VIEW";
    public static final String PRODUCT_MANAGE     = "PRODUCT_MANAGE";
    public static final String PRODUCT_VIEW_COST  = "PRODUCT_VIEW_COST";
    public static final String PRODUCT_VIEW_STOCK = "PRODUCT_VIEW_STOCK";

    // PURCHASE
    public static final String PURCHASE_VIEW   = "PURCHASE_VIEW";
    public static final String PURCHASE_MANAGE = "PURCHASE_MANAGE";

    // SALES
    public static final String QUOTATION_VIEW   = "QUOTATION_VIEW";
    public static final String QUOTATION_MANAGE = "QUOTATION_MANAGE";
    public static final String INVOICE_VIEW     = "INVOICE_VIEW";
    public static final String INVOICE_CREATE   = "INVOICE_CREATE";
    public static final String INVOICE_CANCEL   = "INVOICE_CANCEL";
    public static final String INVOICE_DISCOUNT_OVERRIDE = "INVOICE_DISCOUNT_OVERRIDE";
    public static final String COUPON_VIEW   = "COUPON_VIEW";
    public static final String COUPON_MANAGE = "COUPON_MANAGE";

    // INVENTORY
    public static final String INVENTORY_VIEW   = "INVENTORY_VIEW";
    public static final String INVENTORY_ADJUST = "INVENTORY_ADJUST";

    // PAYMENT
    public static final String PAYMENT_VIEW   = "PAYMENT_VIEW";
    public static final String PAYMENT_MANAGE = "PAYMENT_MANAGE";

    // EXPENSE
    public static final String EXPENSE_VIEW   = "EXPENSE_VIEW";
    public static final String EXPENSE_MANAGE = "EXPENSE_MANAGE";

    // REPORT
    public static final String REPORT_VIEW      = "REPORT_VIEW";
    public static final String REPORT_FINANCIAL = "REPORT_FINANCIAL";

    // SETTINGS
    public static final String SETTINGS_VIEW   = "SETTINGS_VIEW";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

    // PROJECT
    public static final String PROJECT_VIEW = "PROJECT_VIEW";
    public static final String PROJECT_MANAGE = "PROJECT_MANAGE";
    public static final String PROJECT_MATERIAL_VIEW = "PROJECT_MATERIAL_VIEW";
    public static final String PROJECT_MATERIAL_MANAGE = "PROJECT_MATERIAL_MANAGE";

    // LABOUR
    public static final String LABOUR_VIEW   = "LABOUR_VIEW";
    public static final String LABOUR_MANAGE = "LABOUR_MANAGE";

    /**
     * DEVELOPER (CR-045) - the only permission in the catalogue that is not an
     * ERP capability, and the only one no default role holds.
     *
     * OWNER is excluded deliberately, and the exclusion is enforced in two
     * places because there are two ways a role gets its grants:
     * V30__developer_inspection_permission.sql skips it for the roles that
     * already exist, and TenantRegistrationServiceImpl filters the DEVELOPER
     * module out of the "OWNER gets everything" grant given to every shop
     * registered afterwards. Running a hardware shop and debugging the
     * software that runs it are different jobs; granting this by default
     * would put a diagnostics console one stolen owner password away.
     *
     * Holding it is still not sufficient - the environment must also permit
     * inspection. See DeveloperInspectionService.
     */
    public static final String DEVELOPER_INSPECT = "DEVELOPER_INSPECT";
}
