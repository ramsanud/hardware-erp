import { Navigate, Outlet } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';

/**
 * CR-065. This DOES gate on `initialising` now, unlike the version this
 * replaces.
 *
 * The old comment here said there was never a silent-refresh attempt in
 * flight on mount to wait for, because the session lived only in memory. That
 * stopped being true when the refresh token moved into an HttpOnly cookie:
 * there is now exactly such an attempt on every load, and without this gate a
 * reload would bounce the admin to the sign-in screen a moment before the
 * restored session arrived - which is the very symptom the cookie was
 * introduced to fix.
 */
export function PlatformAdminProtectedRoute() {
  const { isAuthenticated, initialising } = usePlatformAdminAuth();

  if (initialising) {
    return (
      <div className="flex h-dvh items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.login} replace />;
  }

  return <Outlet />;
}
