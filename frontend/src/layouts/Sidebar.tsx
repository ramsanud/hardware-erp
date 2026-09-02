import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  Boxes, Calculator, CalendarCheck, ChevronDown, ClipboardList, Coins, FileClock, FileDown, FileText, HardHat, KeyRound, Landmark,
  LayoutDashboard, Layers, Package, PackageSearch, Settings,
  ShieldCheck, ShoppingBag, ShoppingCart, Tags, TerminalSquare, Ticket, TrendingUp, Truck,
  UserCheck, UserCircle, Users, Wallet, Wrench,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { AUTH_ROUTES, PERMISSIONS } from '@/modules/auth/constants';
import { DEVELOPER_ROUTES } from '@/modules/developer/constants';
import { brandService } from '@/modules/settings/services/brandService';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { APP_NAME } from '@/shared/constants';
import { cn } from '@/shared/lib/utils';
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

interface NavSection {
  title: string;
  icon: LucideIcon;
  items: NavItem[];
}

/**
 * Grouped by workflow, not by database table.
 *
 * Customer -> Quotation -> Invoice -> Payment reads down the Sales section;
 * Supplier -> Purchase order -> Bill reads down Purchase. Someone learning the
 * software can follow their actual job down the rail.
 */
const NAV_SECTIONS: NavSection[] = [
  {
    title: 'Overview',
    icon: LayoutDashboard,
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, available: true },
    ],
  },
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
      { to: AUTH_ROUTES.profile, label: 'My profile', icon: UserCircle, available: true },
      { to: AUTH_ROUTES.users, label: 'Users', icon: Users, permission: PERMISSIONS.USER_VIEW, available: true },
      { to: AUTH_ROUTES.roles, label: 'Roles', icon: ShieldCheck, permission: PERMISSIONS.ROLE_VIEW, available: true },
      { to: AUTH_ROUTES.permissions, label: 'Permissions', icon: KeyRound, permission: PERMISSIONS.ROLE_VIEW, available: true },
      { to: AUTH_ROUTES.auditLog, label: 'Security log', icon: FileClock, permission: PERMISSIONS.AUDIT_VIEW, available: true },
      { to: '/settings/shop', label: 'Shop settings', icon: Settings, permission: PERMISSIONS.SETTINGS_VIEW, available: true },
    ],
  },
  {
    // Its own section rather than an entry under Administration, because
    // administering a shop and debugging the software are different jobs -
    // the same distinction that keeps DEVELOPER_INSPECT off the OWNER role
    // (CR-045). In production nobody holds the permission and the server
    // refuses regardless, so this section renders for nobody there.
    title: 'Developer',
    icon: TerminalSquare,
    items: [
      { to: DEVELOPER_ROUTES.inspection, label: 'Inspection', icon: TerminalSquare, permission: PERMISSIONS.DEVELOPER_INSPECT, available: true },
    ],
  },
];

/** Every route reachable from the rail, for the command palette to search. */
export function navigableItems(hasPermission: (permission: string) => boolean) {
  return NAV_SECTIONS.flatMap((section) =>
    section.items
      .filter((item) => item.available)
      .filter((item) => !item.permission || hasPermission(item.permission))
      .map((item) => ({
        to: item.to,
        label: item.label,
        icon: item.icon,
        section: section.title,
      })),
  );
}

interface SidebarNavProps {
  collapsed?: boolean;
  onNavigate?: () => void;
}

export function SidebarNav({ collapsed = false, onNavigate }: SidebarNavProps) {
  const { hasPermission } = useAuth();
  // Every section starts expanded - collapsing one is a per-viewer choice,
  // never a way to lose track of a module that exists (CR-023).
  const [collapsedSections, setCollapsedSections] = useState<Set<string>>(new Set());

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items
      .filter((item) => item.available)
      .filter((item) => !item.permission || hasPermission(item.permission)),
  })).filter((section) => section.items.length > 0);

  const toggleSection = (title: string) => {
    setCollapsedSections((current) => {
      const next = new Set(current);
      if (next.has(title)) next.delete(title); else next.add(title);
      return next;
    });
  };

  return (
    <nav className="flex flex-col gap-0.5 px-2 pb-6" aria-label="Main">
      {sections.map((section) => {
        const sectionCollapsed = collapsedSections.has(section.title);
        return (
          <div key={section.title}>
            {collapsed ? (
              <div
                className="mx-3 my-2 h-px"
                style={{ background: 'hsl(var(--sidebar-border))' }}
                aria-hidden
              />
            ) : (
              <button
                type="button"
                onClick={() => toggleSection(section.title)}
                aria-expanded={!sectionCollapsed}
                className="sidebar-section flex w-full items-center gap-1.5 rounded-sm px-1 py-0.5
                           transition-colors hover:bg-sidebar-hover/60"
              >
                <section.icon className="h-3 w-3" aria-hidden />
                <span className="flex-1 text-left">{section.title}</span>
                <ChevronDown
                  className={cn('h-3 w-3 shrink-0 transition-transform', sectionCollapsed && '-rotate-90')}
                  aria-hidden
                />
              </button>
            )}

            {(!sectionCollapsed || collapsed) ? section.items.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                onClick={onNavigate}
                title={collapsed ? label : undefined}
                className={cn('sidebar-link', collapsed && 'justify-center px-2')}
              >
                {({ isActive }) => (
                  <span
                    data-active={isActive}
                    className={cn(
                      'flex w-full items-center gap-3',
                      collapsed && 'justify-center',
                    )}
                  >
                    <Icon className="h-4 w-4 shrink-0" aria-hidden />
                    {collapsed ? null : <span className="truncate">{label}</span>}
                  </span>
                )}
              </NavLink>
            )) : null}
          </div>
        );
      })}
    </nav>
  );
}

export function SidebarBrand({ collapsed = false }: { collapsed?: boolean }) {
  const { brandName, hasLogo, logoVersion } = useAppChrome();

  const logoSrc = useAuthenticatedImage(hasLogo ? brandService.logoUrl : null, logoVersion);

  return (
    <div
      className="flex h-16 items-center gap-2.5 px-4"
      style={{ borderBottom: '1px solid hsl(var(--sidebar-border))' }}
    >
      <span
        className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-lg"
        style={{ background: 'hsl(var(--sidebar-active))' }}
      >
        {logoSrc ? (
          <img src={logoSrc} alt={`${brandName ?? APP_NAME} logo`} className="h-full w-full object-cover" />
        ) : (
          <Wrench className="h-4 w-4 text-white" aria-hidden />
        )}
      </span>
      {collapsed ? null : (
        <span
          className="truncate text-sm font-medium leading-tight"
          style={{ color: 'hsl(var(--sidebar-foreground))' }}
        >
          {brandName ?? APP_NAME}
        </span>
      )}
    </div>
  );
}

export function SidebarFooter({ collapsed = false }: { collapsed?: boolean }) {
  if (collapsed) return null;
  return (
    <div
      className="px-4 py-3 text-[11px] leading-relaxed"
      style={{
        color: 'hsl(var(--sidebar-section))',
        borderTop: '1px solid hsl(var(--sidebar-border))',
      }}
    >
      <p>&copy; {new Date().getFullYear()} U.Ram sangar</p>
    </div>
  );
}
