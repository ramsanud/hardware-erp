import { AUTH_ROUTES } from '@/modules/auth/constants';

/**
 * CR-075. A one-time tip for the first visit to each screen.
 *
 * The welcome tour explains the shape of the business; this explains the
 * screen in front of you, at the moment you first see it. Keyed by route
 * prefix and matched longest-first, so /invoices/53 gets the Invoices tip and
 * /labour/attendance gets its own rather than a generic Labour one.
 *
 * No permission field here, deliberately: a tip is only ever shown on a page
 * the user has already reached, and the route guard has done the gating by
 * then. A tip for a page you cannot open is a tip you will never see.
 *
 * Copy rule: say what the screen is FOR and the one thing most people come
 * here to do. Not a feature list - that is what the screen itself is for.
 */
export interface PageTip {
  /** Stable id - persisted per user in the seen-set, so renaming one re-shows it. */
  id: string;
  title: string;
  body: string;
}

export const PAGE_TIPS: Record<string, PageTip> = {
  '/dashboard': {
    id: 'dashboard',
    title: 'Today at a glance',
    body: 'Sales, money owed and low stock, all on one screen. Each card opens the list behind it - use them as shortcuts rather than the menu.',
  },
  '/quotations': {
    id: 'quotations',
    title: 'Quote before you bill',
    body: 'A quotation is a price offer with no stock or money movement. When the customer agrees, open it and choose Convert to invoice - nothing is retyped.',
  },
  '/invoices': {
    id: 'invoices',
    title: 'An invoice is the sale',
    body: 'Creating one reduces stock and adds to what the customer owes. Invoices are cancelled, never deleted, so the numbering stays unbroken for GST.',
  },
  '/customers': {
    id: 'customers',
    title: 'Who you sell to',
    body: 'Open a customer to see every invoice and payment against their name and what is still outstanding - check here before extending credit.',
  },
  '/payments': {
    id: 'payments',
    title: 'Money received',
    body: 'Record each payment against the invoice it settles. A part-payment is fine; the invoice shows the balance until it is cleared.',
  },
  '/coupons': {
    id: 'coupons',
    title: 'Discounts with a name',
    body: 'A coupon is a discount rule you can hand out and switch off later. Applying one at invoice time records exactly why the price was lower.',
  },
  '/projects': {
    id: 'projects',
    title: 'Work done at a site',
    body: 'A project collects the materials issued and the labour attended for one job, so you can see what it actually cost before you price the next one.',
  },
  '/labour/workers': {
    id: 'labour-workers',
    title: 'Your workers',
    body: 'Register each worker once with their daily rate. Attendance and wages are calculated from what is entered here.',
  },
  '/labour/attendance': {
    id: 'labour-attendance',
    title: 'Mark attendance daily',
    body: 'Tick who worked today and on which project. Wages and project costs come straight from this, so a day missed here is a day nobody is paid for.',
  },
  '/purchases': {
    id: 'purchases',
    title: 'What you bought',
    body: 'Record a purchase against its supplier and the stock goes up automatically. You can import a supplier bill and preview every line before it is saved.',
  },
  '/suppliers': {
    id: 'suppliers',
    title: 'Who you buy from',
    body: 'Each supplier shows what you have bought and what you still owe them. A supplier is never deleted, because old purchases still point at them.',
  },
  '/products': {
    id: 'products',
    title: 'What you sell',
    body: 'Price, tax rate and unit live here. Stock is separate - a product with no stock yet is normal, it just cannot be sold until a purchase brings some in.',
  },
  '/categories': {
    id: 'categories',
    title: 'Group your products',
    body: 'Categories keep the product list findable. You can also add one from inside the product form without losing what you typed.',
  },
  '/brands': {
    id: 'brands',
    title: 'Brands',
    body: 'A brand is optional on a product but useful for searching and reports. Add one here or from the product form.',
  },
  '/stock': {
    id: 'stock',
    title: 'What is on the shelf',
    body: 'Every sale, purchase and adjustment moves stock automatically. Open a product to see the movements that led to today\'s number.',
  },
  '/expenses': {
    id: 'expenses',
    title: 'Everything that is not a purchase',
    body: 'Rent, transport, wages, tea - if money went out and it was not for stock, it goes here. Reports read this, so profit is only right if this is.',
  },
  '/tools/gst-calculator': {
    id: 'gst-calculator',
    title: 'Quick GST arithmetic',
    body: 'Work out tax-inclusive and exclusive amounts without leaving the app. Nothing here is saved - it is a calculator, not a record.',
  },
  '/tools/tally-export': {
    id: 'tally-export',
    title: 'Hand the books to your accountant',
    body: 'Export a period in a form Tally can import. Pick the dates, download, and send the file on.',
  },
  [AUTH_ROUTES.profile]: {
    id: 'profile',
    title: 'Your own account',
    body: 'Change your password, photo and appearance here. Appearance is per person - your colleague at the next counter can have a different look.',
  },
  '/support': {
    id: 'support',
    title: 'Ask for help',
    body: 'Raise a ticket and follow its replies here. For anything urgent, Contact admin in the menu under your name goes straight to email.',
  },
  [AUTH_ROUTES.users]: {
    id: 'users',
    title: 'Staff accounts',
    body: 'You create every account - there is no self-signup. Give each person the role that matches their job; they see only what that role allows.',
  },
  [AUTH_ROUTES.roles]: {
    id: 'roles',
    title: 'What each job may do',
    body: 'A role is a named bundle of permissions. Change the role and everyone holding it changes with it - no need to edit users one by one.',
  },
  [AUTH_ROUTES.permissions]: {
    id: 'permissions',
    title: 'The building blocks of a role',
    body: 'Every screen and action is guarded by one of these. This list is for reference; assign them through Roles.',
  },
  [AUTH_ROUTES.auditLog]: {
    id: 'security-audit-log',
    title: 'Who signed in, and what changed about access',
    body: 'Sign-ins, failed attempts, password resets and permission changes. This is the security trail - business changes are in the Activity log.',
  },
  [AUTH_ROUTES.activityLog]: {
    id: 'activity-log',
    title: 'Every business change, before and after',
    body: 'Who changed what, when, and what the value was before. Nothing is deleted from the books, so this is how you find out what happened.',
  },
  '/settings/shop': {
    id: 'shop-settings',
    title: 'The shop itself',
    body: 'Name, GSTIN, address, logo and invoice numbering. What is set here prints on every document, so check it once carefully.',
  },
};

/** Longest defined prefix that matches the path, or null. */
export function tipForPath(pathname: string): PageTip | null {
  let best: string | null = null;
  for (const prefix of Object.keys(PAGE_TIPS)) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
      if (best === null || prefix.length > best.length) best = prefix;
    }
  }
  return best ? PAGE_TIPS[best] : null;
}

/** localStorage key bases; scoped per user id by themeScope. */
export const PAGE_TIPS_SEEN_KEY = 'hardware-erp-page-tips-seen';
export const PAGE_TIPS_OFF_KEY = 'hardware-erp-page-tips-off';
