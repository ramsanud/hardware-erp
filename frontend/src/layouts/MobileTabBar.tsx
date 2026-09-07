import { NavLink } from 'react-router-dom';
import {
  Boxes, ClipboardList, FileText, HardHat, Home, MoreHorizontal, Package,
  ShoppingCart, Users, Wallet,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { PERMISSIONS } from '@/modules/auth/constants';
import { cn } from '@/shared/lib/utils';

interface TabCandidate {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Undefined means every signed-in user sees it. */
  permission?: string;
}

/**
 * CR-061. Priority order, not a fixed set. The first four the signed-in user
 * is actually entitled to become the tabs; the rest of the rail stays one tap
 * away under "More".
 *
 * Ordered around the counter workflow of a hardware shop - bill a customer,
 * check a price, look up who owes what - so the everyday job needs no menu.
 * The tail entries exist so a user who holds none of the sales permissions
 * (Purchase Staff, a Warehouse hand) still gets four working tabs rather than
 * a bar with one Home button and three gaps.
 */
const TAB_CANDIDATES: TabCandidate[] = [
  { to: '/dashboard', label: 'Home', icon: Home },
  { to: '/invoices', label: 'Invoices', icon: FileText, permission: PERMISSIONS.INVOICE_VIEW },
  { to: '/products', label: 'Products', icon: Package, permission: PERMISSIONS.PRODUCT_VIEW },
  { to: '/customers', label: 'Customers', icon: Users, permission: PERMISSIONS.CUSTOMER_VIEW },
  { to: '/purchases', label: 'Purchases', icon: ShoppingCart, permission: PERMISSIONS.PURCHASE_VIEW },
  { to: '/quotations', label: 'Quotes', icon: ClipboardList, permission: PERMISSIONS.QUOTATION_VIEW },
  { to: '/stock', label: 'Stock', icon: Boxes, permission: PERMISSIONS.INVENTORY_VIEW },
  { to: '/payments', label: 'Payments', icon: Wallet, permission: PERMISSIONS.PAYMENT_VIEW },
  { to: '/projects', label: 'Projects', icon: HardHat, permission: PERMISSIONS.PROJECT_VIEW },
];

const MAX_TABS = 4;

interface MobileTabBarProps {
  onOpenMore: () => void;
  moreOpen: boolean;
}

/**
 * The primary navigation below the `lg` rail. Replaces the hamburger as the
 * default way to move around: a drawer hides every destination behind a tap
 * and a read, which is why the phone build read as a shrunken admin panel.
 *
 * Deliberately painted from --background/--primary rather than the --sidebar
 * tokens the rail uses. The rail is fixed deep navy in both themes on purpose
 * (it reads as chrome beside a bright working area), but a navy slab pinned
 * across the bottom of a phone is not chrome, it is a third of the screen -
 * and it would have ignored the user's chosen colour theme entirely.
 */
export function MobileTabBar({ onOpenMore, moreOpen }: MobileTabBarProps) {
  const { hasPermission } = useAuth();

  const tabs = TAB_CANDIDATES
    .filter((tab) => !tab.permission || hasPermission(tab.permission))
    .slice(0, MAX_TABS);

  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-40 border-t bg-background/95 backdrop-blur
                 supports-[backdrop-filter]:bg-background/85 lg:hidden"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <ul className="flex items-stretch">
        {tabs.map(({ to, label, icon: Icon }) => (
          <li key={to} className="min-w-0 flex-1">
            <NavLink to={to} className="tab-bar-item">
              {({ isActive }) => (
                <span data-active={isActive} className="tab-bar-item-inner">
                  <span className="tab-bar-icon">
                    <Icon className="h-[1.125rem] w-[1.125rem]" aria-hidden />
                  </span>
                  <span className="tab-bar-label">{label}</span>
                </span>
              )}
            </NavLink>
          </li>
        ))}

        <li className="min-w-0 flex-1">
          <button
            type="button"
            onClick={onOpenMore}
            aria-expanded={moreOpen}
            aria-label="More destinations"
            className={cn('tab-bar-item w-full')}
          >
            <span data-active={moreOpen} className="tab-bar-item-inner">
              <span className="tab-bar-icon">
                <MoreHorizontal className="h-[1.125rem] w-[1.125rem]" aria-hidden />
              </span>
              <span className="tab-bar-label">More</span>
            </span>
          </button>
        </li>
      </ul>
    </nav>
  );
}
