import { PERMISSIONS } from '@/modules/auth/constants';

/**
 * The dashboard widget catalog (CR-066) - one source of truth for every
 * widget's id, display name, description and permission.
 *
 * Naming is deliberately locked here rather than written inline at each call
 * site. The same widget is named in three places once the picker ships - the
 * card header, the picker list, and the saved layout - and three hand-typed
 * copies of "Low Stock Items" drift into "Low stock", "Low Stock Alert" and
 * "Stock alerts" within a release. The `id` is what persists in a saved
 * layout, so it follows the naming law's kebab-noun convention and must never
 * be renamed once a layout references it; the `name` is free to be reworded.
 *
 * `dataSource` records whether the widget can actually be rendered today.
 * This is not decoration - fourteen of these have no aggregate endpoint yet
 * and four have no underlying domain at all, so a picker that offered all
 * thirty-two would promise data the server cannot produce. The picker reads
 * this field to disable what is not ready, and CR-066 records what each
 * unavailable one would require.
 */

export type WidgetCategory =
  | 'overview'
  | 'sales'
  | 'payments'
  | 'purchases'
  | 'inventory'
  | 'finance'
  | 'operations';

export type WidgetDataSource =
  /** An endpoint exists and returns this today. */
  | 'available'
  /** The domain exists; the aggregate query/endpoint does not yet. */
  | 'needs-endpoint'
  /** No table backs this at all - it needs its own module first. */
  | 'unavailable';

export type WidgetId =
  | 'total-sales' | 'todays-sales' | 'outstanding-customer-balance' | 'products-count'
  | 'suppliers-count' | 'customers-count' | 'low-stock-count' | 'invoices-count'
  | 'sales-trend' | 'sales-by-category' | 'recent-invoices' | 'top-selling-products'
  | 'top-customers'
  | 'outstanding-payments' | 'recent-payments' | 'payment-summary'
  | 'purchase-summary' | 'recent-purchases' | 'supplier-outstanding'
  | 'inventory-snapshot' | 'low-stock-list' | 'out-of-stock-list' | 'stock-value'
  | 'fast-moving-products'
  | 'expenses-summary' | 'cash-balance' | 'bank-balance' | 'gst-summary'
  | 'action-required' | 'recent-activity' | 'tasks-reminders' | 'pending-quotations';

export interface WidgetDefinition {
  id: WidgetId;
  /** Shown on the card header and in the picker. Title Case, no trailing punctuation. */
  name: string;
  /** One line, sentence case - what the widget answers, not how it is drawn. */
  description: string;
  category: WidgetCategory;
  /** Hidden from the picker unless the signed-in user holds this. The server checks again. */
  permission: string;
  dataSource: WidgetDataSource;
  /** Why it is not renderable yet. Set on every widget that is not 'available'. */
  blockedReason?: string;
}

export const WIDGET_CATEGORY_LABELS: Record<WidgetCategory, string> = {
  overview: 'Overview',
  sales: 'Sales',
  payments: 'Payments',
  purchases: 'Purchases',
  inventory: 'Inventory',
  finance: 'Finance',
  operations: 'Operations',
};

/** Picker section order. Overview first because it is what an owner opens the app for. */
export const WIDGET_CATEGORY_ORDER: WidgetCategory[] = [
  'overview', 'sales', 'payments', 'purchases', 'inventory', 'finance', 'operations',
];

export const DASHBOARD_WIDGETS: WidgetDefinition[] = [
  // -- Overview ------------------------------------------------------------
  {
    id: 'total-sales',
    name: 'Total Sales',
    description: 'Invoiced revenue across all time.',
    category: 'overview',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'available',
  },
  {
    id: 'todays-sales',
    name: "Today's Sales",
    description: 'Revenue invoiced today, against yesterday for comparison.',
    category: 'overview',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'available',
  },
  {
    id: 'outstanding-customer-balance',
    name: 'Customer Receivables',
    description: 'Total still owed by customers on unpaid invoices.',
    category: 'overview',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'available',
  },
  {
    id: 'products-count',
    name: 'Total Products',
    description: 'Number of products in the catalogue.',
    category: 'overview',
    permission: PERMISSIONS.PRODUCT_VIEW,
    dataSource: 'available',
  },
  {
    id: 'suppliers-count',
    name: 'Total Suppliers',
    description: 'Number of active suppliers on record.',
    category: 'overview',
    permission: PERMISSIONS.SUPPLIER_VIEW,
    dataSource: 'available',
  },
  {
    id: 'customers-count',
    name: 'Total Customers',
    description: 'Number of customers on record.',
    category: 'overview',
    permission: PERMISSIONS.CUSTOMER_VIEW,
    dataSource: 'available',
  },
  {
    id: 'low-stock-count',
    name: 'Low Stock Items',
    description: 'How many products have fallen to or below their reorder level.',
    category: 'overview',
    permission: PERMISSIONS.INVENTORY_VIEW,
    dataSource: 'available',
  },
  {
    id: 'invoices-count',
    name: 'Total Invoices',
    description: 'Number of invoices raised.',
    category: 'overview',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'available',
  },

  // -- Sales ---------------------------------------------------------------
  {
    id: 'sales-trend',
    name: 'Revenue & Sales Trend',
    description: 'Revenue over time, bucketed by day, week or month.',
    category: 'sales',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'available',
  },
  {
    id: 'sales-by-category',
    name: 'Category Revenue Breakdown',
    description: 'Which product categories the revenue came from.',
    category: 'sales',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'available',
  },
  {
    id: 'recent-invoices',
    name: 'Recent Invoices',
    description: 'The latest invoices with their payment status.',
    category: 'sales',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'available',
  },
  {
    id: 'top-selling-products',
    name: 'Top Selling Products',
    description: 'Best sellers by revenue for the chosen period.',
    category: 'sales',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'No aggregate over invoice lines by product exists yet.',
  },
  {
    id: 'top-customers',
    name: 'Key Accounts & Top Customers',
    description: 'The customers contributing most of the revenue.',
    category: 'sales',
    permission: PERMISSIONS.CUSTOMER_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'No aggregate over invoices grouped by customer exists yet.',
  },

  // -- Payments ------------------------------------------------------------
  {
    id: 'outstanding-payments',
    name: 'Overdue Receivables',
    description: 'Invoices past their due date and still unpaid.',
    category: 'payments',
    permission: PERMISSIONS.PAYMENT_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'Invoice search has no overdue filter or ageing query.',
  },
  {
    id: 'recent-payments',
    name: 'Payment Collections',
    description: 'Payments received most recently.',
    category: 'payments',
    permission: PERMISSIONS.PAYMENT_VIEW,
    dataSource: 'available',
  },
  {
    id: 'payment-summary',
    name: 'Collections Overview',
    description: 'What was collected in the period, by payment mode.',
    category: 'payments',
    permission: PERMISSIONS.PAYMENT_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'No aggregate over payments grouped by mode exists yet.',
  },

  // -- Purchases -----------------------------------------------------------
  {
    id: 'purchase-summary',
    name: 'Purchase Summary',
    description: 'What was purchased in the period and what it cost.',
    category: 'purchases',
    permission: PERMISSIONS.PURCHASE_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'No aggregate over purchase bills exists yet.',
  },
  {
    id: 'recent-purchases',
    name: 'Recent Purchases',
    description: 'The latest purchase bills entered.',
    category: 'purchases',
    permission: PERMISSIONS.PURCHASE_VIEW,
    dataSource: 'available',
  },
  {
    id: 'supplier-outstanding',
    name: 'Vendor Payables',
    description: 'Total still owed to suppliers on unpaid bills.',
    category: 'purchases',
    permission: PERMISSIONS.PURCHASE_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'No supplier-side outstanding aggregate exists; only the customer side is computed.',
  },

  // -- Inventory -----------------------------------------------------------
  {
    id: 'inventory-snapshot',
    name: 'Stock Overview',
    description: 'Stock position across the catalogue at a glance.',
    category: 'inventory',
    permission: PERMISSIONS.INVENTORY_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'Stock search returns rows, not the in-stock/low/out counts this needs.',
  },
  {
    id: 'low-stock-list',
    name: 'Low Stock Alert',
    description: 'Products at or below their reorder level, worst first.',
    category: 'inventory',
    permission: PERMISSIONS.INVENTORY_VIEW,
    dataSource: 'available',
  },
  {
    id: 'out-of-stock-list',
    name: 'Out of Stock Items',
    description: 'Products with no sellable quantity left.',
    category: 'inventory',
    permission: PERMISSIONS.INVENTORY_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'GET /v1/stock has only a lowStockOnly flag; there is no zero-quantity filter.',
  },
  {
    id: 'stock-value',
    name: 'Total Inventory Valuation',
    description: 'What the stock on hand is worth at cost.',
    category: 'inventory',
    permission: PERMISSIONS.PRODUCT_VIEW_COST,
    dataSource: 'needs-endpoint',
    blockedReason: 'No valuation query summing quantity against cost price exists yet.',
  },
  {
    id: 'fast-moving-products',
    name: 'Fast Moving Items',
    description: 'Products leaving the shelf quickest by stock movement.',
    category: 'inventory',
    permission: PERMISSIONS.INVENTORY_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'No aggregate over stock_movement by product and period exists yet.',
  },

  // -- Finance -------------------------------------------------------------
  {
    id: 'expenses-summary',
    name: 'Expense Breakdown',
    description: 'What was spent in the period, by expense category.',
    category: 'finance',
    permission: PERMISSIONS.EXPENSE_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'No aggregate over expenses grouped by category exists yet.',
  },
  {
    id: 'cash-balance',
    name: 'Cash-in-Hand',
    description: 'Cash held at the counter after receipts and payouts.',
    category: 'finance',
    permission: PERMISSIONS.REPORT_FINANCIAL,
    dataSource: 'unavailable',
    blockedReason: 'No cash ledger exists in the schema. Needs a cash book module before this can be anything but a guess.',
  },
  {
    id: 'bank-balance',
    name: 'Bank Account Balance',
    description: 'Balance held across the shop bank accounts.',
    category: 'finance',
    permission: PERMISSIONS.REPORT_FINANCIAL,
    dataSource: 'unavailable',
    blockedReason: 'tenant_bank_account stores collection details (IFSC, UPI, QR) and has no balance column. Needs a reconciliation ledger.',
  },
  {
    id: 'gst-summary',
    name: 'GST Liability Summary',
    description: 'Output tax against input credit for the period.',
    category: 'finance',
    permission: PERMISSIONS.REPORT_FINANCIAL,
    dataSource: 'unavailable',
    blockedReason: 'No GST return or liability computation exists anywhere in the backend.',
  },

  // -- Operations ----------------------------------------------------------
  {
    id: 'action-required',
    name: 'Action Required',
    description: 'Everything waiting on someone: overdue, unpaid or unconfirmed.',
    category: 'operations',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'Needs one endpoint gathering overdue invoices, expiring quotations and low stock into a single list.',
  },
  {
    id: 'recent-activity',
    name: 'Audit & Activity Log',
    description: 'Who changed what, most recent first.',
    category: 'operations',
    permission: PERMISSIONS.AUDIT_VIEW,
    dataSource: 'needs-endpoint',
    blockedReason: 'activity_log is written by every module but no controller reads it back.',
  },
  {
    id: 'tasks-reminders',
    name: 'Tasks & Follow-ups',
    description: 'Your open tasks and the follow-ups due next.',
    category: 'operations',
    permission: PERMISSIONS.INVOICE_VIEW,
    dataSource: 'unavailable',
    blockedReason: 'No task entity exists. notification/reminder is a low-stock alert scheduler, not user-owned tasks.',
  },
  {
    id: 'pending-quotations',
    name: 'Pending Quotations',
    description: 'Quotations sent but not yet accepted or rejected.',
    category: 'operations',
    permission: PERMISSIONS.QUOTATION_VIEW,
    dataSource: 'available',
  },
];

/** Lookup by id - the saved-layout path resolves ids to definitions on every render. */
export const WIDGET_BY_ID: Record<WidgetId, WidgetDefinition> = Object.fromEntries(
  DASHBOARD_WIDGETS.map((w) => [w.id, w]),
) as Record<WidgetId, WidgetDefinition>;

/** The widgets that can render today. The picker offers the rest disabled, with `blockedReason` as the tooltip. */
export const AVAILABLE_WIDGETS = DASHBOARD_WIDGETS.filter((w) => w.dataSource === 'available');
