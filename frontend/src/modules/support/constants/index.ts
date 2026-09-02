export const SUPPORT_ROUTES = {
  list: '/support',
  detail: (id: number | string) => `/support/${id}`,
} as const;

export const TICKET_CATEGORY_OPTIONS = [
  { value: 'LOGIN', label: 'Login' },
  { value: 'INVOICE', label: 'Invoice' },
  { value: 'PAYMENT', label: 'Payment' },
  { value: 'PURCHASE', label: 'Purchase' },
  { value: 'INVENTORY', label: 'Inventory' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
  { value: 'SUBSCRIPTION', label: 'Subscription' },
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'OTHER', label: 'Other' },
] as const;
