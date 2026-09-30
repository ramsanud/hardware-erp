package com.hardware.erp.subscription.entity;

/**
 * CR-088. The one compile-time list of plan-gated features. The `feature`
 * table (V57) is authoritative for names, descriptions and which plan
 * carries what; this enum exists so FeatureAccessService.requireFeature()
 * calls are checked by the compiler rather than being free-text keys.
 * FeatureCatalogConsistencyTest asserts every constant here has a row.
 *
 * Plan membership is NOT here - it lives in plan_feature, so a platform
 * operator can move a feature between plans without a release.
 */
public enum FeatureKey {
    // BASIC
    GST_BILLING, NON_GST_BILLING, INVOICE_CANCELLATION, PAYMENTS, PRODUCTS, LOW_STOCK_ALERT,
    CUSTOMERS, SUPPLIERS, INVENTORY, DASHBOARD, BASIC_REPORTS, DATA_EXPORT, OFFLINE_SYNC,
    // PRO
    QUOTATION, SALES_ORDER, DELIVERY_CHALLAN, CREDIT_NOTE, PURCHASE_RETURN, INVOICE_TEMPLATES,
    THERMAL_PRINT, BULK_IMPORT, PDF_PRICE_IMPORT, PRICE_CHANGE_DETECTION, STOCK_VALUATION,
    CUSTOMER_CREDIT, PAYMENT_REMINDERS, SUPPLIER_STATEMENT, ADVANCED_REPORTS, STAFF_ROLES,
    PROJECTS, COUPONS,
    // PREMIUM
    SMART_SUBSTITUTE, SMART_INSIGHTS, ADVANCED_ANALYTICS, MULTI_BRANCH, NEARBY_PRODUCT_DISCOVERY,
    ADVANCED_NOTIFICATIONS, AUTO_BACKUP, AI_FEATURES, PRIORITY_SUPPORT
}
