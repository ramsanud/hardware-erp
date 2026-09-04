import { Navigate, Outlet } from 'react-router-dom';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';

/**
 * No "initialising" state to gate on, unlike the tenant ProtectedRoute - a
 * platform-admin session lives only in memory (see platformAdminTokenStorage),
 * so there is never a silent-refresh attempt in flight on mount to wait for.
 */
export function PlatformAdminProtectedRoute() {
  const { isAuthenticated } = usePlatformAdminAuth();

  if (!isAuthenticated) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.login} replace />;
  }

  return <Outlet />;
}
