import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  ActivitySquare, AlertTriangle, BarChart3, Building2, FileClock, Flag, LayoutGrid, LifeBuoy, LogOut, Menu, Settings, ShieldAlert, ShieldCheck, TerminalSquare,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Sheet, SheetContent, SheetTitle } from '@/shared/components/ui/sheet';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import { ModeToggle } from '@/theme/ModeToggle';
import { cn, initials } from '@/shared/lib/utils';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { PLATFORM_ADMIN_ROUTES } from '../constants';

/**
 * Only nav items that are real, working pages - section 6/62 of the
 * Platform Admin spec forbids "navigation without functionality." A `permission`
 * hides an item entirely (not just disables it) when the signed-in admin's
 * role does not hold it - this is UX only, the backend's own @PreAuthorize
 * on each endpoint is the actual enforcement. Grows one entry per phase as
 * it ships, never ahead of it.
 */
const NAV_ITEMS = [
  { to: PLATFORM_ADMIN_ROUTES.dashboard, label: 'Overview', icon: LayoutGrid, permission: null },
  { to: PLATFORM_ADMIN_ROUTES.tenants, label: 'Tenants', icon: Building2, permission: 'TENANT_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.analytics, label: 'Analytics', icon: BarChart3, permission: 'ANALYTICS_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.systemHealth, label: 'System Health', icon: ActivitySquare, permission: 'SYSTEM_HEALTH_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.incidents, label: 'Incidents', icon: AlertTriangle, permission: 'SYSTEM_HEALTH_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.support, label: 'Support Center', icon: LifeBuoy, permission: 'SUPPORT_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.auditLog, label: 'Audit Log', icon: FileClock, permission: 'AUDIT_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.developerTools, label: 'Developer Tools', icon: TerminalSquare, permission: 'DEVELOPER_TOOLS_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.security, label: 'Security Center', icon: ShieldAlert, permission: null },
  { to: PLATFORM_ADMIN_ROUTES.featureFlags, label: 'Feature Flags', icon: Flag, permission: 'FEATURE_FLAG_VIEW' },
  { to: PLATFORM_ADMIN_ROUTES.settings, label: 'Platform Settings', icon: Settings, permission: 'BILLING_VIEW' },
] as const;

export function PlatformAdminLayout() {
  const { admin, logout } = usePlatformAdminAuth();
  const navigate = useNavigate();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate(PLATFORM_ADMIN_ROUTES.login, { replace: true });
  };

  return (
    <div className="flex min-h-dvh bg-muted/20">
      <aside className="hidden w-64 shrink-0 border-r bg-background lg:block">
        <div className="sticky top-0 flex h-dvh flex-col">
          <SidebarBrand />
          <div className="flex-1 overflow-y-auto px-3 py-2">
            <SidebarNavList onNavigate={() => {}} permissions={admin?.permissions ?? []} />
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-40 flex h-16 items-center gap-2 border-b bg-background/95 px-3 backdrop-blur sm:px-4">
          <Button
            variant="ghost" size="icon" className="lg:hidden"
            onClick={() => setMobileNavOpen(true)}
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" />
          </Button>

          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold">Platform Admin</span>
            <Badge variant="outline" className="hidden sm:inline-flex">
              {import.meta.env.PROD ? 'PRODUCTION' : 'DEVELOPMENT'}
            </Badge>
          </div>

          <div className="flex-1" />

          <ModeToggle />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="gap-2 px-2">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                  {initials(admin?.fullName)}
                </span>
                <span className="hidden max-w-[10rem] truncate text-sm font-medium sm:inline">
                  {admin?.fullName}
                </span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-64">
              <DropdownMenuLabel>
                <p className="truncate">{admin?.fullName}</p>
                <p className="truncate text-xs font-normal text-muted-foreground">{admin?.email}</p>
                <div className="mt-2 flex items-center gap-2">
                  <Badge variant="secondary">{admin?.role}</Badge>
                  <Badge variant={admin?.mfaEnabled ? 'default' : 'destructive'} className="gap-1">
                    <ShieldCheck className="h-3 w-3" />
                    {admin?.mfaEnabled ? 'MFA on' : 'MFA off'}
                  </Badge>
                </div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem destructive onClick={handleLogout}>
                <LogOut className="h-4 w-4" />
                Sign out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>

        <main className="flex-1 px-3 py-5 sm:px-5 lg:px-8">
          <div className="mx-auto w-full max-w-7xl space-y-5">
            <Outlet />
          </div>
        </main>
      </div>

      <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
        <SheetContent side="left" showClose={false}>
          <SheetTitle className="sr-only">Navigation</SheetTitle>
          <SidebarBrand />
          <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
            <SidebarNavList onNavigate={() => setMobileNavOpen(false)} permissions={admin?.permissions ?? []} />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}

function SidebarBrand() {
  return (
    <div className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
      <span className="flex h-8 w-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
        <ShieldCheck className="h-4 w-4" />
      </span>
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold leading-tight">Hardware ERP</p>
        <p className="truncate text-xs leading-tight text-muted-foreground">Platform Admin</p>
      </div>
    </div>
  );
}

function SidebarNavList({ onNavigate, permissions }: { onNavigate: () => void; permissions: string[] }) {
  const visibleItems = NAV_ITEMS.filter((item) => item.permission === null || permissions.includes(item.permission));
  return (
    <nav className="space-y-1">
      {visibleItems.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          onClick={onNavigate}
          className={({ isActive }) => cn(
            'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
            isActive
              ? 'bg-primary/10 text-primary'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground',
          )}
        >
          <Icon className="h-4 w-4 shrink-0" />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}
