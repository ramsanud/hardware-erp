export const CATEGORY_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;

export const BRAND_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;

export const PRODUCT_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;

/** Common hardware-shop units. Free text is still accepted - this is a shortlist, not a lock. */
export const PRODUCT_UNIT_SUGGESTIONS = [
  'PCS', 'BOX', 'SET', 'PAIR', 'KG', 'GRAM', 'METER', 'FEET', 'LITRE', 'ROLL', 'DOZEN',
] as const;

/** Matches ProductController.SORTABLE. Anything else falls back server-side. */
export const PRODUCT_SORT_FIELDS = [
  { value: 'productName', label: 'Name' },
  { value: 'productCode', label: 'Code' },
  { value: 'status', label: 'Status' },
  { value: 'createdAt', label: 'Created' },
] as const;

export const PRODUCT_ROUTES = {
  list: '/products',
  detail: (id: number | string) => `/products/${id}`,
  categories: '/categories',
  brands: '/brands',
} as const;
