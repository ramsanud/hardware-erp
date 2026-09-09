/**
 * Shared API fixtures.
 *
 * Every shape here mirrors a real DTO. Where a stub drifts from the DTO the
 * test stops testing the application and starts testing the stub - which is
 * how an early version of the responsive sweep "found" three crashes that
 * were entirely its own fault.
 */
import { envelope, pageOf } from './harness.mjs';

/** auth/dto/UserResponse.java */
export const OWNER = {
  id: 1,
  fullName: 'Ramesh Kumar',
  mobileNo: '9876543210',
  email: 'owner@shop.in',
  employeeCode: 'EMP001',
  roleId: 1,
  roleCode: 'OWNER',
  roleName: 'Owner',
  permissions: [
    'USER_VIEW', 'USER_MANAGE', 'ROLE_VIEW', 'ROLE_MANAGE', 'AUDIT_VIEW',
    'CUSTOMER_VIEW', 'CUSTOMER_MANAGE', 'SUPPLIER_VIEW', 'SUPPLIER_MANAGE',
    'PRODUCT_VIEW', 'PRODUCT_MANAGE', 'PRODUCT_VIEW_COST', 'PRODUCT_VIEW_STOCK',
    'PURCHASE_VIEW', 'PURCHASE_MANAGE', 'QUOTATION_VIEW', 'QUOTATION_MANAGE',
    'INVOICE_VIEW', 'INVOICE_CREATE', 'INVENTORY_VIEW', 'PAYMENT_VIEW',
    'EXPENSE_VIEW', 'EXPENSE_MANAGE', 'REPORT_VIEW', 'REPORT_FINANCIAL',
    'SETTINGS_VIEW', 'SETTINGS_MANAGE', 'COUPON_VIEW', 'COUPON_MANAGE',
    'PROJECT_VIEW', 'PROJECT_MANAGE', 'LABOUR_VIEW', 'LABOUR_MANAGE',
  ],
  status: 'ACTIVE',
  mustChangePassword: false,
  lastLoginAt: null,
  createdAt: '2026-01-01T10:00:00',
};

/** product/dto/ProductSummaryResponse.java */
export function product(id, name, status, price = '1,250.00') {
  return {
    id,
    productCode: `P-${String(id).padStart(4, '0')}`,
    productName: name,
    categoryName: 'Plumbing',
    brandName: 'Ashirvad',
    unit: 'PCS',
    sellingPriceDisplay: price,
    gstRatePercent: '18',
    status,
    hasImage: false,
  };
}

export const PRODUCTS = [
  product(1, 'CPVC Elbow 25mm', 'ACTIVE'),
  product(2, 'Brass Ball Valve 15mm', 'INACTIVE'),
  product(3, 'PVC Pipe 4 inch ISI marked heavy duty', 'ACTIVE'),
];

/**
 * A signed-in owner with the product catalogue loaded.
 *
 * The empty-page fallback at the end matters: the sweep visits 25 routes and
 * only a few are stubbed explicitly. Answering the rest with a well-formed
 * EMPTY page keeps them on their empty-state path, which still proves the
 * route renders without crashing or overflowing.
 */
export function signedInApi({ products = PRODUCTS, user = OWNER } = {}) {
  return (url) => {
    if (url.includes('/v1/auth/refresh')) {
      return envelope({ accessToken: 'stub-access-token', mustChangePassword: false, user });
    }
    if (url.includes('/v1/auth/captcha-config')) return envelope({ enabled: false, siteKey: null });
    if (url.includes('/v1/auth/me')) return envelope(user);
    if (url.includes('/v1/products')) return pageOf(products);
    if (url.includes('/v1/categories')) return envelope([{ id: 1, categoryName: 'Plumbing', status: 'ACTIVE' }]);
    if (url.includes('/v1/brands')) return envelope([{ id: 1, brandName: 'Ashirvad', status: 'ACTIVE' }]);
    // Collection endpoints that answer with a bare array, not a page.
    if (/\/(cities|states|summary|options|sessions|permissions|roles|work-types|expense-categories|banks)(\?|$)/.test(url)) {
      return envelope([]);
    }
    if (/page=|size=|\?/.test(url)) return pageOf([]);
    return envelope(null);
  };
}

/** Signed OUT: only the config lookup the login form makes on mount. */
export function signedOutApi() {
  return (url) => {
    if (url.includes('/v1/auth/captcha-config')) return envelope({ enabled: false, siteKey: null });
    // Anything else 401s the way a real unauthenticated call would, so the
    // app takes its genuine signed-out path instead of a stubbed happy one.
    return { status: 401, contentType: 'application/json',
      body: JSON.stringify({ success: false, message: 'Unauthorized', code: 'UNAUTHORIZED' }) };
  };
}

export const VIEWPORTS = [
  { name: 'mobile-S', width: 320, height: 720, mobile: true },
  { name: 'mobile-L', width: 414, height: 896, mobile: true },
  { name: 'tablet', width: 768, height: 1024, mobile: false },
  { name: 'laptop', width: 1280, height: 800, mobile: false },
  { name: 'desktop', width: 1920, height: 1080, mobile: false },
];
