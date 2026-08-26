/**
 * Permission codes. Mirrors backend auth/entity/PermissionCode.java.
 *
 * Used only to hide UI the server would reject anyway. It is never the
 * enforcement point: every guarded call is checked again by @PreAuthorize.
 *
 * All 31 codes seeded by V1__auth_schema.sql are listed, including those whose
 * modules have not shipped yet. The sidebar needs them to decide what a role
 * would be allowed to see once the module exists.
 */
export const PERMISSIONS = {
  USER_VIEW: 'USER_VIEW',
  USER_MANAGE: 'USER_MANAGE',
  ROLE_VIEW: 'ROLE_VIEW',
  ROLE_MANAGE: 'ROLE_MANAGE',
  AUDIT_VIEW: 'AUDIT_VIEW',
  CUSTOMER_VIEW: 'CUSTOMER_VIEW',
  CUSTOMER_MANAGE: 'CUSTOMER_MANAGE',
  SUPPLIER_VIEW: 'SUPPLIER_VIEW',
  SUPPLIER_MANAGE: 'SUPPLIER_MANAGE',
  SUPPLIER_VIEW_BANK_ACCOUNT: 'SUPPLIER_VIEW_BANK_ACCOUNT',
  PRODUCT_VIEW: 'PRODUCT_VIEW',
  PRODUCT_MANAGE: 'PRODUCT_MANAGE',
  PRODUCT_VIEW_COST: 'PRODUCT_VIEW_COST',
  PRODUCT_VIEW_STOCK: 'PRODUCT_VIEW_STOCK',
  PURCHASE_VIEW: 'PURCHASE_VIEW',
  PURCHASE_MANAGE: 'PURCHASE_MANAGE',
  QUOTATION_VIEW: 'QUOTATION_VIEW',
  QUOTATION_MANAGE: 'QUOTATION_MANAGE',
  INVOICE_VIEW: 'INVOICE_VIEW',
  INVOICE_CREATE: 'INVOICE_CREATE',
  INVOICE_CANCEL: 'INVOICE_CANCEL',
  INVOICE_DISCOUNT_OVERRIDE: 'INVOICE_DISCOUNT_OVERRIDE',
  INVENTORY_VIEW: 'INVENTORY_VIEW',
  INVENTORY_ADJUST: 'INVENTORY_ADJUST',
  PAYMENT_VIEW: 'PAYMENT_VIEW',
  PAYMENT_MANAGE: 'PAYMENT_MANAGE',
  EXPENSE_VIEW: 'EXPENSE_VIEW',
  EXPENSE_MANAGE: 'EXPENSE_MANAGE',
  REPORT_VIEW: 'REPORT_VIEW',
  REPORT_FINANCIAL: 'REPORT_FINANCIAL',
  SETTINGS_VIEW: 'SETTINGS_VIEW',
  SETTINGS_MANAGE: 'SETTINGS_MANAGE',
  COUPON_VIEW: 'COUPON_VIEW',
  COUPON_MANAGE: 'COUPON_MANAGE',
  PROJECT_VIEW: 'PROJECT_VIEW',
  PROJECT_MANAGE: 'PROJECT_MANAGE',
  PROJECT_MATERIAL_VIEW: 'PROJECT_MATERIAL_VIEW',
  PROJECT_MATERIAL_MANAGE: 'PROJECT_MATERIAL_MANAGE',
  LABOUR_VIEW: 'LABOUR_VIEW',
  LABOUR_MANAGE: 'LABOUR_MANAGE',

  /**
   * CR-045. The only code here that is not an ERP capability, and the only
   * one no default role holds - OWNER included. Gates the Developer rail
   * entry and page; the server gates the data, and additionally refuses
   * outright in production whatever permission the caller holds.
   */
  DEVELOPER_INSPECT: 'DEVELOPER_INSPECT',
} as const;

export const USER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'SUSPENDED', label: 'Suspended' },
] as const;

export const ROLE_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;

/** Matches UserController.SORTABLE. Anything else falls back server-side. */
export const USER_SORT_FIELDS = [
  { value: 'fullName', label: 'Name' },
  { value: 'mobileNo', label: 'Mobile' },
  { value: 'employeeCode', label: 'Employee code' },
  { value: 'status', label: 'Status' },
  { value: 'createdAt', label: 'Created' },
  { value: 'lastLoginAt', label: 'Last login' },
] as const;

export const AUTH_ROUTES = {
  login: '/login',
  register: '/register',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  forceChangePassword: '/change-password',
  profile: '/profile',
  appearance: '/profile/appearance',
  users: '/users',
  roles: '/roles',
  permissions: '/permissions',
  auditLog: '/security-audit-log',
} as const;
