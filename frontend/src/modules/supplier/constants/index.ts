export const SUPPLIER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'BLOCKED', label: 'Blocked' },
] as const;

/** Matches SupplierController.SORTABLE. Anything else falls back server-side. */
export const SUPPLIER_SORT_FIELDS = [
  { value: 'supplierName', label: 'Name' },
  { value: 'supplierCode', label: 'Code' },
  { value: 'city', label: 'City' },
  { value: 'paymentTermsDays', label: 'Payment terms' },
  { value: 'status', label: 'Status' },
  { value: 'createdAt', label: 'Created' },
] as const;

export const SUPPLIER_ROUTES = {
  list: '/suppliers',
  create: '/suppliers/new',
  detail: (id: number | string) => `/suppliers/${id}`,
  edit: (id: number | string) => `/suppliers/${id}/edit`,
} as const;
