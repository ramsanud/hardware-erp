export const CUSTOMER_ROUTES = {
  list: '/customers',
  detail: (id: number | string) => `/customers/${id}`,
};

export const CUSTOMER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
] as const;
