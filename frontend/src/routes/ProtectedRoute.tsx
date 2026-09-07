import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { AUTH_ROUTES } from '@/modules/auth/constants';

/** Every screen that exists to get the user signed in, rather than to be visited. */
const AUTH_PATHS: string[] = [
  AUTH_ROUTES.login,
  AUTH_ROUTES.mfaVerify,
  AUTH_ROUTES.mfaEnroll,
  AUTH_ROUTES.register,
  AUTH_ROUTES.forgotPassword,
  AUTH_ROUTES.resetPassword,
  AUTH_ROUTES.forceChangePassword,
];

export function isAuthRoute(pathname: string): boolean {
  return AUTH_PATHS.includes(pathname);
}

export function ProtectedRoute() {
  const { isAuthenticated, initialising, mustChangePassword } = useAuth();
  const location = useLocation();

  // Blocking here avoids a flash of the login screen while the startup
  // refresh is still in flight.
  if (initialising) {
    return (
      <div className="flex h-dvh items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  if (!isAuthenticated) {
    /*
     * BUG-FE-027. `from` lets the login page return the user where they were
     * headed - but /change-password is never such a place. Completing a forced
     * change signs every session out, so the user arrives at /login from
     * /change-password on the ordinary success path. Recording that as `from`
     * meant the next sign-in was redirected straight back to the
     * change-password screen, which - with mustChangePassword now false -
     * asked them to replace a password they had just replaced, with no way
     * forward except editing the URL.
     *
     * Any auth screen is wrong here for the same reason: it is a step in
     * getting signed in, never a destination to be returned to afterwards.
     */
    const returnable = !isAuthRoute(location.pathname);
    return (
      <Navigate
        to={AUTH_ROUTES.login}
        state={returnable ? { from: location } : undefined}
        replace
      />
    );
  }

  // A forced password change blocks the whole app, not just sensitive pages.
  if (mustChangePassword && location.pathname !== AUTH_ROUTES.forceChangePassword) {
    // No `from`: this is the app steering the user, not the user asking for a
    // page, so there is nothing here worth returning them to afterwards.
    return <Navigate to={AUTH_ROUTES.forceChangePassword} replace />;
  }

  return <Outlet />;
}
