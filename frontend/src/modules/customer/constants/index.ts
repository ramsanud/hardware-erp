export const CUSTOMER_ROUTES = {
  list: '/customers',
  /** CR-082: opens the list with the create dialog already open. */
  create: '/customers?new=1',
  detail: (id: number | string) => `/customers/${id}`,
};

export const CUSTOMER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;
