import { useEffect, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import {
  LifeBuoy, LogOut, Menu, MonitorSmartphone, PanelLeftClose, PanelLeftOpen, UserCircle,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Sheet, SheetContent, SheetTitle } from '@/shared/components/ui/sheet';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import { Badge } from '@/shared/components/ui/badge';
import { GlobalSearch } from '@/shared/components/GlobalSearch';
import { ModeToggle } from '@/theme/ModeToggle';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { AUTH_ROUTES } from '@/modules/auth/constants';
import { avatarService } from '@/modules/auth/services/avatarService';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { cn, initials } from '@/shared/lib/utils';
import { AiChatWidget } from '@/modules/ai/components/AiChatWidget';
import { ContactAdminDialog } from '@/modules/notification/components/ContactAdminDialog';
import { AppChromeProvider, useAppChrome } from './AppChromeProvider';
import { SidebarBrand, SidebarFooter, SidebarNav } from './Sidebar';

export function AppLayout() {
  return (
    <AppChromeProvider>
      <AppLayoutInner />
    </AppChromeProvider>
  );
}

function AppLayoutInner() {
  const { user, logout, logoutAll } = useAuth();
  const { avatarVersion, brandName } = useAppChrome();
  const avatarSrc = useAuthenticatedImage(avatarService.url, avatarVersion);
  const navigate = useNavigate();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [contactAdminOpen, setContactAdminOpen] = useState(false);
  // Persisted so the rail stays how the user left it across sessions. This is
  // layout preference, not a credential, so localStorage is fine here.
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem('hardware-erp-sidebar-collapsed') === 'true',
  );

  useEffect(() => {
    localStorage.setItem('hardware-erp-sidebar-collapsed', String(collapsed));
  }, [collapsed]);

  const handleLogout = async () => {
    await logout();
    navigate(AUTH_ROUTES.login, { replace: true });
  };

  const handleLogoutAll = async () => {
    await logoutAll();
    navigate(AUTH_ROUTES.login, { replace: true });
  };

  return (
    <div className="flex min-h-dvh">
      {/* Persistent rail from lg up; a dialog below that. */}
      <aside
        className={cn(
          'hidden shrink-0 transition-[width] duration-200 lg:block',
          collapsed ? 'w-[68px]' : 'w-64',
        )}
        style={{ background: 'hsl(var(--sidebar))' }}
      >
        <div className="sticky top-0 flex h-dvh flex-col">
          <SidebarBrand collapsed={collapsed} />
          <div className="flex-1 overflow-y-auto">
            <SidebarNav collapsed={collapsed} />
          </div>
          <SidebarFooter collapsed={collapsed} />
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-40 flex h-16 items-center gap-2 border-b bg-background/95 px-3 backdrop-blur supports-[backdrop-filter]:bg-background/80 sm:px-4">
          <Button
            variant="ghost" size="icon" className="lg:hidden"
            onClick={() => setMobileNavOpen(true)}
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" />
          </Button>

          <Button
            variant="ghost" size="icon" className="hidden lg:inline-flex"
            onClick={() => setCollapsed((value) => !value)}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
          </Button>

          <GlobalSearch />

          {/* GlobalSearch is hidden below sm, which left the phone header as a
              hamburger and two icons with a wide gap between them. The sidebar
              brand is behind the drawer at that width, so nothing on screen
              said which shop you were signed into. */}
          <span className="truncate text-sm font-semibold sm:hidden">{brandName}</span>

          <div className="flex-1" />

          <ModeToggle />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="gap-2 px-2">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary/10 text-xs font-semibold text-primary">
                  {avatarSrc ? (
                    <img src={avatarSrc} alt={user?.fullName ?? 'Profile photo'} className="h-full w-full object-cover object-[50%_28%]" />
                  ) : (
                    initials(user?.fullName)
                  )}
                </span>
                <span className="hidden max-w-[10rem] truncate text-sm font-medium sm:inline">
                  {user?.fullName}
                </span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-60">
              <DropdownMenuLabel>
                <p className="truncate">{user?.fullName}</p>
                <p className="truncate text-xs font-normal text-muted-foreground">
                  {user?.email ?? user?.mobileNo}
                </p>
                <Badge variant="secondary" className="mt-2">{user?.roleName}</Badge>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => navigate(AUTH_ROUTES.profile)}>
                <UserCircle className="h-4 w-4" />
                My profile
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setContactAdminOpen(true)}>
                <LifeBuoy className="h-4 w-4" />
                Contact admin
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleLogoutAll}>
                <MonitorSmartphone className="h-4 w-4" />
                Sign out of all devices
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem destructive onClick={handleLogout}>
                <LogOut className="h-4 w-4" />
                Sign out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>

        {/*
          pb-24 (not py-5's default) - the floating AiChatWidget sits fixed
          at bottom-5 with its own 12x12 footprint, so ordinary py-5 bottom
          padding let a long table's last rows or a chart's own x-axis
          labels render directly underneath it. This is clearance for the
          FAB, not a general spacing change.
        */}
        <main className="flex-1 px-3 pb-24 pt-5 sm:px-5 lg:px-8">
          <div className="mx-auto w-full max-w-7xl space-y-5">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Drawer for anything below the lg rail - phones and tablet portrait.
          Brand and footer are pinned; only the nav list scrolls, matching the
          desktop rail's behaviour instead of scrolling the panel as one block. */}
      <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
        <SheetContent
          side="left"
          showClose={false}
          style={{ background: 'hsl(var(--sidebar))' }}
        >
          <SheetTitle className="sr-only">Navigation</SheetTitle>
          <SidebarBrand />
          <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
            <SidebarNav onNavigate={() => setMobileNavOpen(false)} />
          </div>
          <SidebarFooter />
        </SheetContent>
      </Sheet>

      <ContactAdminDialog open={contactAdminOpen} onOpenChange={setContactAdminOpen} />

      <AiChatWidget />
    </div>
  );
}
