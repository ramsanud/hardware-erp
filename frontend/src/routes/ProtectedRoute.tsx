import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { AUTH_ROUTES } from '@/modules/auth/constants';

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
    // `from` lets the login page return the user where they were headed.
    return <Navigate to={AUTH_ROUTES.login} state={{ from: location }} replace />;
  }

  // A forced password change blocks the whole app, not just sensitive pages.
  if (mustChangePassword && location.pathname !== AUTH_ROUTES.forceChangePassword) {
    return <Navigate to={AUTH_ROUTES.forceChangePassword} replace />;
  }

  return <Outlet />;
}
