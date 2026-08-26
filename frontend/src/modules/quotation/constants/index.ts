export const QUOTATION_ROUTES = {
  list: '/quotations',
  create: '/quotations/new',
  detail: (id: number | string) => `/quotations/${id}`,
};

export const QUOTATION_STATUS_OPTIONS = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'SENT', label: 'Sent' },
  { value: 'ACCEPTED', label: 'Accepted' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'CONVERTED', label: 'Converted' },
] as const;
