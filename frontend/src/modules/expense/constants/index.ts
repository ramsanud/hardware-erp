export const EXPENSE_ROUTES = {
  list: '/expenses',
};

export const EXPENSE_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'CANCELLED', label: 'Cancelled' },
] as const;
