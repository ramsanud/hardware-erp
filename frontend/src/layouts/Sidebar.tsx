import { useEffect, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import {
  Boxes, Calculator, CalendarCheck, ChevronDown, ChevronRight, ClipboardList, Coins, CreditCard, FileClock, FileDown,
  FileText, HardHat, History, KeyRound, Landmark, LayoutDashboard, Layers, LifeBuoy, MessageCircle, Package,
  PackageSearch, PanelLeftClose, Settings, ShieldCheck, ShoppingBag, ShoppingCart, Store, Tags, TerminalSquare,
  Ticket, TrendingUp, Truck, UserCheck, Users, Wallet,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { AUTH_ROUTES, PERMISSIONS } from '@/modules/auth/constants';
import { DEVELOPER_ROUTES } from '@/modules/developer/constants';
import { SUPPORT_ROUTES } from '@/modules/support/constants';
import { SETTINGS_ROUTES } from '@/modules/settings/constants';
import { SUBSCRIPTION_ROUTES } from '@/modules/subscription/constants';
import { brandService } from '@/modules/settings/services/brandService';
import { avatarService } from '@/modules/auth/services/avatarService';
import { whatsAppConnectionService } from '@/modules/settings/services/whatsAppConnectionService';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { APP_NAME } from '@/shared/constants';
import { cn, initials } from '@/shared/lib/utils';
import { BrandGlyph } from '@/shared/components/BrandMark';
import { readScoped, writeScoped } from '@/theme/themeScope';
import { useAppChrome } from './AppChromeProvider';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Undefined means every signed-in user sees it. */
  permission?: string;
  /**
   * False until that module's backend actually exists.
   *
   * Deliberate: a link that opens an empty page is worse than no link. The shop
   * owner learns the menu is unreliable and stops trusting it. Flip this to
   * true in the same commit that ships the module.
   */
  available: boolean;
}

interface NavGroup {
  title: string;
  icon: LucideIcon;
  items: NavItem[];
}

/**
 * CR-082. The rail as the approved dashboard mockup draws it: one direct
 * "Overview" row, then collapsible groups that look like rows themselves,
 * then a short utility list, then the person signed in.
 *
 * Grouped by workflow, not by database table. Customer -> Quotation ->
 * Invoice -> Payment reads down Sales; Supplier -> Purchase reads down
 * Purchase. Someone learning the software can follow their job down the rail.
 */
const OVERVIEW: NavItem = { to: '/dashboard', label: 'Overview', icon: LayoutDashboard, available: true };

const NAV_GROUPS: NavGroup[] = [
  {
    title: 'Sales',
    icon: ShoppingBag,
    items: [
      { to: '/quotations', label: 'Quotations', icon: ClipboardList, permission: PERMISSIONS.QUOTATION_VIEW, available: true },
      { to: '/invoices', label: 'Invoices', icon: FileText, permission: PERMISSIONS.INVOICE_VIEW, available: true },
      { to: '/customers', label: 'Customers', icon: Users, permission: PERMISSIONS.CUSTOMER_VIEW, available: true },
      { to: '/payments', label: 'Payments', icon: Wallet, permission: PERMISSIONS.PAYMENT_VIEW, available: true },
      { to: '/coupons', label: 'Coupons', icon: Ticket, permission: PERMISSIONS.COUPON_VIEW, available: true },
    ],
  },
  {
    title: 'Projects',
    icon: HardHat,
    items: [
      { to: '/projects', label: 'Projects', icon: HardHat, permission: PERMISSIONS.PROJECT_VIEW, available: true },
      { to: '/labour/workers', label: 'Workers', icon: UserCheck, permission: PERMISSIONS.LABOUR_VIEW, available: true },
      { to: '/labour/attendance', label: 'Attendance', icon: CalendarCheck, permission: PERMISSIONS.LABOUR_VIEW, available: true },
    ],
  },
  {
    title: 'Purchase',
    icon: Truck,
    items: [
      { to: '/purchases', label: 'Purchases', icon: ShoppingCart, permission: PERMISSIONS.PURCHASE_VIEW, available: true },
      { to: '/suppliers', label: 'Suppliers', icon: Truck, permission: PERMISSIONS.SUPPLIER_VIEW, available: true },
    ],
  },
  {
    title: 'Inventory',
    icon: Boxes,
    items: [
      { to: '/products', label: 'Products', icon: Package, permission: PERMISSIONS.PRODUCT_VIEW, available: true },
      { to: '/categories', label: 'Categories', icon: Layers, permission: PERMISSIONS.PRODUCT_VIEW, available: true },
      { to: '/brands', label: 'Brands', icon: Tags, permission: PERMISSIONS.PRODUCT_VIEW, available: true },
      { to: '/stock', label: 'Stock', icon: Boxes, permission: PERMISSIONS.INVENTORY_VIEW, available: true },
      { to: '/stock-adjustments', label: 'Stock adjustments', icon: PackageSearch, permission: PERMISSIONS.INVENTORY_ADJUST, available: false },
    ],
  },
  {
    title: 'Accounting',
    icon: Landmark,
    items: [
      { to: '/expenses', label: 'Expenses', icon: Coins, permission: PERMISSIONS.EXPENSE_VIEW, available: true },
      { to: '/ledgers', label: 'Ledgers', icon: Landmark, permission: PERMISSIONS.REPORT_FINANCIAL, available: false },
      { to: '/reports', label: 'Reports', icon: TrendingUp, permission: PERMISSIONS.REPORT_VIEW, available: false },
      // CR-053 backlog item 7 - pure client-side arithmetic, no permission
      // gate: it reads no tenant data, so there is nothing to protect.
      { to: '/tools/gst-calculator', label: 'GST calculator', icon: Calculator, available: true },
      { to: '/tools/tally-export', label: 'Tally export', icon: FileDown, permission: PERMISSIONS.REPORT_FINANCIAL, available: true },
    ],
  },
  {
    title: 'Administration',
    icon: Settings,
    items: [
      { to: AUTH_ROUTES.users, label: 'Users', icon: Users, permission: PERMISSIONS.USER_VIEW, available: true },
      { to: AUTH_ROUTES.roles, label: 'Roles', icon: ShieldCheck, permission: PERMISSIONS.ROLE_VIEW, available: true },
      { to: AUTH_ROUTES.permissions, label: 'Permissions', icon: KeyRound, permission: PERMISSIONS.ROLE_VIEW, available: true },
      { to: AUTH_ROUTES.auditLog, label: 'Security log', icon: FileClock, permission: PERMISSIONS.AUDIT_VIEW, available: true },
      { to: AUTH_ROUTES.activityLog, label: 'Activity log', icon: History, permission: PERMISSIONS.AUDIT_VIEW, available: true },
      { to: SETTINGS_ROUTES.shop, label: 'Shop settings', icon: Settings, permission: PERMISSIONS.SETTINGS_VIEW, available: true },
      { to: SUBSCRIPTION_ROUTES.pricing, label: 'Subscription', icon: CreditCard, permission: PERMISSIONS.SETTINGS_VIEW, available: true },
    ],
  },
  {
    // Its own group rather than an entry under Administration, because
    // administering a shop and debugging the software are different jobs -
    // the same distinction that keeps DEVELOPER_INSPECT off the OWNER role
    // (CR-045). In production nobody holds the permission and the server
    // refuses regardless, so this group renders for nobody there.
    title: 'Developer',
    icon: TerminalSquare,
    items: [
      { to: DEVELOPER_ROUTES.inspection, label: 'Inspection', icon: TerminalSquare, permission: PERMISSIONS.DEVELOPER_INSPECT, available: true },
    ],
  },
];

/**
 * The short list under the groups. "My profile" and "Support" used to live
 * under Administration; the mockup gives them their own places - the person
 * card at the foot of the rail, and these two rows above it - so they are
 * not repeated inside a group.
 */
const UTILITY: NavItem[] = [
  { to: SETTINGS_ROUTES.whatsapp, label: 'WhatsApp Reminders', icon: MessageCircle, permission: PERMISSIONS.SETTINGS_VIEW, available: true },
  { to: SUPPORT_ROUTES.list, label: 'Help & Support', icon: LifeBuoy, available: true },
];

const PROFILE: NavItem = { to: AUTH_ROUTES.profile, label: 'My profile', icon: Users, available: true };

const visible = (items: NavItem[], hasPermission: (p: string) => boolean) =>
  items.filter((item) => item.available).filter((item) => !item.permission || hasPermission(item.permission));

/** Every route reachable from the rail, for the command palette to search. */
export function navigableItems(hasPermission: (permission: string) => boolean) {
  const entry = (item: NavItem, section: string) => ({ to: item.to, label: item.label, icon: item.icon, section });
  return [
    entry(OVERVIEW, 'Overview'),
    ...NAV_GROUPS.flatMap((group) => visible(group.items, hasPermission).map((item) => entry(item, group.title))),
    ...visible(UTILITY, hasPermission).map((item) => entry(item, 'Help')),
    entry(PROFILE, 'Account'),
  ];
}

/**
 * Which groups this person has folded. Per user, like every other rail
 * preference (CR-068's precedent). Every group starts OPEN: folding one is a
 * per-viewer choice, never a way to lose track of a module that exists
 * (CR-023) - the mockup's tidy folded state is something you arrive at, not
 * something you are handed.
 */
const FOLDED_KEY = 'hardware-erp-rail-folded';

function readFolded(): Set<string> {
  try {
    const raw = readScoped(FOLDED_KEY);
    return new Set(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set();
  }
}

interface SidebarNavProps {
  collapsed?: boolean;
  onNavigate?: () => void;
}

export function SidebarNav({ collapsed = false, onNavigate }: SidebarNavProps) {
  const { hasPermission } = useAuth();
  const [folded, setFolded] = useState<Set<string>>(readFolded);
  /*
   * The green dot beside "WhatsApp Reminders" means the shop's WhatsApp is
   * actually connected - read from /v1/settings/whatsapp, which the same
   * SETTINGS_VIEW that shows the row already permits. Not connected, or the
   * call fails: no dot. A dot that is always green would be decoration.
   */
  const [whatsAppConnected, setWhatsAppConnected] = useState(false);
  const canSeeWhatsApp = hasPermission(PERMISSIONS.SETTINGS_VIEW);
  useEffect(() => {
    if (!canSeeWhatsApp) return;
    let cancelled = false;
    whatsAppConnectionService.getStatus()
      .then((status) => { if (!cancelled) setWhatsAppConnected(Boolean(status?.connected)); })
      .catch(() => { if (!cancelled) setWhatsAppConnected(false); });
    return () => { cancelled = true; };
  }, [canSeeWhatsApp]);

  const groups = NAV_GROUPS
    .map((group) => ({ ...group, items: visible(group.items, hasPermission) }))
    .filter((group) => group.items.length > 0);
  const utility = visible(UTILITY, hasPermission);

  const toggle = (title: string) => {
    setFolded((current) => {
      const next = new Set(current);
      if (next.has(title)) next.delete(title); else next.add(title);
      writeScoped(FOLDED_KEY, JSON.stringify([...next]));
      return next;
    });
  };

  /*
    No render-prop and no data-active on links (BUG-FE-036). NavLink puts
    aria-current="page" on the anchor itself and .sidebar-link styles off
    that, so the highlight cannot fall out of step with the element the CSS
    matches.
  */
  const link = ({ to, label, icon: Icon }: NavItem, indent = false) => (
    <NavLink
      key={to}
      to={to}
      onClick={onNavigate}
      // The label is unreadable when collapsed, so it becomes the hover
      // tooltip and the accessible name instead.
      title={collapsed ? label : undefined}
      aria-label={collapsed ? label : undefined}
      className={cn('sidebar-link', collapsed && 'justify-center px-2', !collapsed && indent && 'pl-9')}
    >
      <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden />
      {collapsed ? null : <span className="truncate">{label}</span>}
      {to === SETTINGS_ROUTES.whatsapp && whatsAppConnected ? (
        <span
          className={cn('h-2 w-2 shrink-0 rounded-full bg-success', collapsed ? 'absolute right-1.5 top-1.5' : 'ml-auto')}
          role="img"
          aria-label="WhatsApp connected"
          data-whatsapp-dot
        />
      ) : null}
    </NavLink>
  );

  return (
    <nav className="flex flex-col gap-0.5 px-2 pb-4" aria-label="Main">
      {link(OVERVIEW)}

      {groups.map((group) => {
        const isFolded = folded.has(group.title);
        return (
          <div key={group.title} className="flex flex-col gap-0.5">
            {collapsed ? (
              <div className="mx-3 my-2 h-px bg-sidebar-border" aria-hidden />
            ) : (
              <button
                type="button"
                onClick={() => toggle(group.title)}
                aria-expanded={!isFolded}
                className="sidebar-group"
              >
                <group.icon className="h-[18px] w-[18px] shrink-0" aria-hidden />
                <span className="flex-1 truncate text-left">{group.title}</span>
                <ChevronDown
                  className={cn('h-4 w-4 shrink-0 opacity-70 transition-transform', isFolded && '-rotate-90')}
                  aria-hidden
                />
              </button>
            )}
            {(!isFolded || collapsed) ? group.items.map((item) => link(item, true)) : null}
          </div>
        );
      })}

      {utility.length > 0 ? (
        <>
          <div className="mx-3 my-2 h-px bg-sidebar-border" aria-hidden />
          {utility.map((item) => link(item))}
        </>
      ) : null}
    </nav>
  );
}

interface SidebarBrandProps {
  collapsed?: boolean;
  /** Omitted by the mobile drawer, which has no rail to collapse. */
  onToggleCollapsed?: () => void;
}

export function SidebarBrand({ collapsed = false, onToggleCollapsed }: SidebarBrandProps) {
  const { brandName, hasLogo, logoVersion } = useAppChrome();
  const { hasPermission } = useAuth();
  const logoSrc = useAuthenticatedImage(hasLogo ? brandService.logoUrl : null, logoVersion);
  const canOpenSettings = hasPermission(PERMISSIONS.SETTINGS_VIEW);

  /*
   * The shop card. The mockup draws it with a dropdown caret, which would
   * promise a shop switcher - and there is none: one login is one shop
   * (CR-016), by design. So it says which shop this is and, for an owner,
   * opens Shop settings; the chevron points right, the way a link does.
   */
  const shopCard = collapsed ? null : (
    <div className="px-3 pb-3">
      {(() => {
        const inner = (
          <>
            <span className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-sidebar-active/15 text-sidebar-active">
              {logoSrc
                ? <img src={logoSrc} alt="" className="h-full w-full object-cover" />
                : <Store className="h-[18px] w-[18px]" aria-hidden />}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-semibold text-sidebar-foreground">{brandName ?? APP_NAME}</span>
              <span className="block truncate text-[11px] text-sidebar-muted">Hardware shop</span>
            </span>
            {canOpenSettings ? <ChevronRight className="h-4 w-4 shrink-0 text-sidebar-muted" aria-hidden /> : null}
          </>
        );
        const className = cn(
          'flex w-full items-center gap-2.5 rounded-lg border border-sidebar-border bg-sidebar-hover/60 p-2.5 text-left',
          canOpenSettings && 'transition-colors hover:bg-sidebar-hover',
        );
        return canOpenSettings
          ? <Link to={SETTINGS_ROUTES.shop} className={className} aria-label={`${brandName ?? APP_NAME} - shop settings`}>{inner}</Link>
          : <div className={className}>{inner}</div>;
      })()}
    </div>
  );

  return (
    <div>
      <div
        className={cn(
          'flex h-16 items-center',
          collapsed ? 'justify-center px-2' : 'gap-2.5 px-4',
        )}
      >
        {/* CR-081: the same post-and-lintel mark as the sign-in page and favicon. */}
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-primary text-primary-foreground">
          <BrandGlyph size={22} />
        </span>
        {collapsed ? null : (
          <>
            <span className="min-w-0 flex-1 truncate text-[17px] font-bold leading-tight tracking-tight text-sidebar-foreground">
              Hardware <span className="text-sidebar-active">ERP</span>
            </span>
            {/*
              The toggle lives beside the name, where the thing it resizes
              actually is. Hidden when collapsed because the rail is 68px wide
              there - the header keeps its own copy, which is the only way back.
            */}
            {onToggleCollapsed ? (
              <button
                type="button"
                onClick={onToggleCollapsed}
                aria-label="Collapse sidebar"
                className="-mr-1 shrink-0 rounded-md p-1.5 text-sidebar-muted transition-colors
                           hover:bg-sidebar-hover hover:text-sidebar-foreground
                           focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-active"
              >
                <PanelLeftClose className="h-4 w-4" aria-hidden />
              </button>
            ) : null}
          </>
        )}
      </div>
      {shopCard}
    </div>
  );
}

/** The person signed in, at the foot of the rail; opens their profile. */
export function SidebarFooter({ collapsed = false }: { collapsed?: boolean }) {
  const { user } = useAuth();
  const { avatarVersion } = useAppChrome();
  const avatarSrc = useAuthenticatedImage(user ? avatarService.url : null, avatarVersion);

  return (
    <div className={cn('border-t border-sidebar-border', collapsed ? 'p-2' : 'p-3')}>
      <Link
        to={PROFILE.to}
        aria-label={`${user?.fullName ?? 'My profile'} - ${PROFILE.label}`}
        className={cn(
          'flex items-center gap-2.5 rounded-lg text-left transition-colors hover:bg-sidebar-hover',
          collapsed ? 'justify-center p-1.5' : 'p-2',
        )}
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-sidebar-active/20 text-sm font-semibold text-sidebar-active">
          {avatarSrc
            ? <img src={avatarSrc} alt="" className="h-full w-full object-cover object-[50%_28%]" />
            : initials(user?.fullName)}
        </span>
        {collapsed ? null : (
          <>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-semibold text-sidebar-foreground">{user?.fullName}</span>
              <span className="block truncate text-[11px] text-sidebar-muted">{user?.roleName}</span>
            </span>
            <ChevronRight className="h-4 w-4 shrink-0 text-sidebar-muted" aria-hidden />
          </>
        )}
      </Link>
    </div>
  );
}
