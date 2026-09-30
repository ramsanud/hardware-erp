/** CR-086 / CR-087. */
export const REPORT_ROUTES = {
  list: '/reports',
  dayBook: '/reports/day-book',
  receivablesAgeing: '/reports/receivables-ageing',
  stockValuation: '/reports/stock-valuation',
  purchaseRegister: '/reports/purchase-register',
  gstSummary: '/reports/gst-summary',
  gstr1: '/reports/gstr1',
  /** CR-091. Its own page, REPORT_FINANCIAL, not one of the ReportsPage tabs. */
  profit: '/reports/profit',
} as const;

export type ReportKey =
  | 'day-book'
  | 'receivables-ageing'
  | 'stock-valuation'
  | 'purchase-register'
  | 'gst-summary'
  | 'gstr1';
