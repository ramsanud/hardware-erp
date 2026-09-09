import { useEffect, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import {
  LifeBuoy, LogOut, MonitorSmartphone, PanelLeftClose, PanelLeftOpen, Search, UserCircle, X,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
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
import { MobileMoreMenu } from './MobileMoreMenu';
import { MobileTabBar } from './MobileTabBar';
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
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
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
      {/* Persistent rail from lg up; a bottom tab bar plus a "More" drawer
          below that (CR-061). */}
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
        {/*
          CR-061: 56px on a phone - the height a native app bar uses - stepping
          up to 64px alongside the rail. The notch inset is applied as padding
          in .app-bar rather than folded into the height, so the bar grows into
          the safe area instead of the title sliding under an iPhone status bar.
        */}
        <header
          className="app-bar sticky top-0 z-40 flex h-14 items-center gap-1 border-b bg-background/95 px-2
                     backdrop-blur supports-[backdrop-filter]:bg-background/80 sm:gap-2 sm:px-4 lg:h-16"
        >
          {mobileSearchOpen ? (
            /* Search takes the whole bar on a phone rather than competing with
               the brand for the ~120px that were left over. */
            <div className="flex w-full items-center gap-1 sm:hidden">
              <GlobalSearch className="block flex-1" autoFocus />
              <Button
                variant="ghost" size="icon"
                onClick={() => setMobileSearchOpen(false)}
                aria-label="Close search"
              >
                <X className="h-5 w-5" />
              </Button>
            </div>
          ) : null}

          <div className={cn('flex w-full items-center gap-1 sm:gap-2', mobileSearchOpen && 'hidden sm:flex')}>
            <Button
              variant="ghost" size="icon" className="hidden lg:inline-flex"
              onClick={() => setCollapsed((value) => !value)}
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            >
              {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
            </Button>

            <GlobalSearch />

            {/* The rail's brand sits behind the drawer on a phone, so without
                this nothing on screen says which shop you are signed into. */}
            <span className="truncate pl-1 text-base font-semibold sm:hidden">{brandName}</span>

            <div className="flex-1" />

            {/* Search is a destination on a phone, not a permanent field. */}
            <Button
              variant="ghost" size="icon" className="sm:hidden"
              onClick={() => setMobileSearchOpen(true)}
              aria-label="Search"
            >
              <Search className="h-5 w-5" />
            </Button>

            <ModeToggle />

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="gap-2 px-1.5 sm:px-2">
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
          </div>
        </header>

        {/*
          Bottom padding is clearance for fixed furniture, not spacing - the
          tab bar below lg plus its safe-area inset, and the floating
          AiChatWidget at every width. Without it a long table's last rows
          render underneath both. See .app-main in index.css.
        */}
        <main className="app-main flex-1 px-3 pt-4 sm:px-5 sm:pt-5 lg:px-8">
          <div className="mx-auto w-full max-w-7xl space-y-4 sm:space-y-5">
            <Outlet />
          </div>
        </main>
      </div>

      <MobileTabBar onOpenMore={() => setMobileNavOpen(true)} moreOpen={mobileNavOpen} />

      {/* The "More" tab's destination - every section the four tabs left out.
          A bottom sheet rising from the tab bar, not the desktop rail in a
          left-hand drawer (CR-062). */}
      <MobileMoreMenu
        open={mobileNavOpen}
        onOpenChange={setMobileNavOpen}
        avatarSrc={avatarSrc}
        onSignOut={handleLogout}
      />

      <ContactAdminDialog open={contactAdminOpen} onOpenChange={setContactAdminOpen} />

      <AiChatWidget />
    </div>
  );
}
