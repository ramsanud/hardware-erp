import {
  Boxes, ClipboardList, Coins, FileText, HardHat, History, LayoutDashboard,
  LifeBuoy, Search, Settings, Truck, Users,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { AUTH_ROUTES, PERMISSIONS } from '@/modules/auth/constants';

/**
 * CR-075. What the tour says, and to whom.
 *
 * Steps are filtered by PERMISSION, never by role code. That is the same rule
 * the sidebar's NAV_SECTIONS already follows, and it is the only version that
 * stays true: roles in this application are rows in a table that the owner
 * edits (CR-008), so a shop with a "Counter staff" role nobody anticipated
 * still gets a coherent tour, and a hard-coded `roleCode === 'ACCOUNTANT'`
 * would have described a screen that person cannot open.
 *
 * It also means the tour can never point at a page the server would refuse:
 * the same predicate hides the rail entry, the route and this step.
 */
export interface TourStep {
  /** Stable id - persisted only in aggregate, but it keeps React keys honest. */
  id: string;
  title: string;
  /** One short paragraph. Written for someone who has never used an ERP. */
  body: string;
  icon: LucideIcon;
  /**
   * Shown when the step is worth acting on immediately. The tour closes and
   * navigates - a tour that cannot take you anywhere is a slideshow.
   */
  action?: { label: string; to: string };
  /**
   * Any one of these is enough to see the step. Undefined means everyone
   * signed in sees it.
   */
  permissions?: string[];
}

/**
 * Ordered as the working day runs, not as the modules were built: what the
 * shop sells, who it sells to, what it buys, what it owes. Someone reading
 * straight through gets the shape of the business, which is the actual point
 * of a first-visit tour.
 */
export const TOUR_STEPS: TourStep[] = [
  {
    id: 'dashboard',
    title: 'Your dashboard',
    body: 'This is the first screen after signing in. It summarises the day so far - what was sold, what is owed, and what needs attention. Everything else hangs off the menu on the left.',
    icon: LayoutDashboard,
    action: { label: 'Open dashboard', to: '/dashboard' },
  },
  {
    id: 'search',
    title: 'Find anything, fast',
    body: 'The search box at the top jumps to any page, product or customer. If you remember only part of a name, type that. It is usually quicker than the menu.',
    icon: Search,
  },
  {
    id: 'sales',
    title: 'Quotation, invoice, payment',
    body: 'Sales run in that order. Quote a price, turn the quotation into an invoice when the customer agrees, then record the money as a payment. Each step carries the last one forward, so nothing is typed twice.',
    icon: FileText,
    action: { label: 'Open invoices', to: '/invoices' },
    permissions: [PERMISSIONS.INVOICE_VIEW, PERMISSIONS.QUOTATION_VIEW],
  },
  {
    id: 'customers',
    title: 'Customers and what they owe',
    body: 'Every customer keeps their own history and outstanding balance. Open one to see every invoice and payment against their name before you give credit.',
    icon: Users,
    action: { label: 'Open customers', to: '/customers' },
    permissions: [PERMISSIONS.CUSTOMER_VIEW],
  },
  {
    id: 'inventory',
    title: 'Products and stock',
    body: 'Products hold the price and tax; stock holds how many are on the shelf. Selling reduces stock automatically, so the count stays right without anyone updating it by hand.',
    icon: Boxes,
    action: { label: 'Open stock', to: '/stock' },
    permissions: [PERMISSIONS.PRODUCT_VIEW, PERMISSIONS.INVENTORY_VIEW],
  },
  {
    id: 'purchase',
    title: 'Suppliers and purchases',
    body: 'Record what you buy against the supplier it came from. A purchase adds to stock the same way a sale removes from it, and the supplier page shows what you still owe them.',
    icon: Truck,
    action: { label: 'Open purchases', to: '/purchases' },
    permissions: [PERMISSIONS.PURCHASE_VIEW],
  },
  {
    id: 'projects',
    title: 'Projects and labour',
    body: 'For work done at a site rather than sold over the counter: track the materials issued to a project and the workers who attended, so the job can be costed honestly.',
    icon: HardHat,
    action: { label: 'Open projects', to: '/projects' },
    permissions: [PERMISSIONS.PROJECT_VIEW, PERMISSIONS.LABOUR_VIEW],
  },
  {
    id: 'expenses',
    title: 'Expenses and reports',
    body: 'Rent, wages, transport and the rest go here. Together with sales and purchases they are what the reports read, so the profit figure is only as good as what gets entered.',
    icon: Coins,
    action: { label: 'Open expenses', to: '/expenses' },
    permissions: [PERMISSIONS.EXPENSE_VIEW, PERMISSIONS.REPORT_VIEW],
  },
  {
    id: 'audit',
    title: 'Nothing disappears quietly',
    body: 'The activity log records every business change with its before and after values, and the security log records sign-ins and permission changes. Records are cancelled, never deleted, so history stays intact.',
    icon: History,
    action: { label: 'Open activity log', to: AUTH_ROUTES.activityLog },
    permissions: [PERMISSIONS.AUDIT_VIEW],
  },
  {
    id: 'people',
    title: 'People and permissions',
    body: 'You create the accounts for your staff and decide what each role may see. Someone who cannot open a screen will not find it in their menu either - the menu is built from the same permissions.',
    icon: Settings,
    action: { label: 'Open users', to: AUTH_ROUTES.users },
    permissions: [PERMISSIONS.USER_VIEW, PERMISSIONS.SETTINGS_VIEW],
  },
  {
    id: 'help',
    title: 'If you get stuck',
    body: 'Use Contact admin in the menu under your name to send a question - you can attach a screenshot. You can reopen this tour any time from the ? button in the top bar.',
    icon: LifeBuoy,
  },
];

/**
 * Bumping this re-shows the tour to everyone, including people who finished
 * the old one. It is the only reason a completed tour ever comes back, so it
 * changes when the tour genuinely says something new - not when a typo is
 * fixed, which would be an unwelcome interruption for no new information.
 */
export const TOUR_VERSION = '1';

/** localStorage key base; the real key is scoped per user id by themeScope. */
export const TOUR_STORAGE_KEY = 'hardware-erp-tour';

export const TOUR_ICON = ClipboardList;
