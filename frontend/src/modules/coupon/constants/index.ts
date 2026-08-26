export const DISCOUNT_TYPE_OPTIONS = [
  { value: 'PERCENT', label: 'Percent' },
  { value: 'FLAT', label: 'Flat amount' },
] as const;

export const COUPON_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;

/** Single list page with a create/edit dialog - no separate create/edit routes. */
export const COUPON_ROUTES = {
  list: '/coupons',
} as const;
