import { Navigate, Outlet } from 'react-router-dom';
import { ShieldX } from 'lucide-react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { EmptyState } from '@/shared/components/EmptyState';

interface RequirePermissionProps {
  permission: string;
  /** Redirect instead of showing the message. Used for the landing route. */
  redirectTo?: string;
}

/**
 * A convenience gate, not a security control. Every guarded endpoint is checked
 * again by @PreAuthorize on the server; this only avoids showing a page that
 * would return 403.
 */
export function RequirePermission({ permission, redirectTo }: RequirePermissionProps) {
  const { hasPermission } = useAuth();

  if (hasPermission(permission)) return <Outlet />;
  if (redirectTo) return <Navigate to={redirectTo} replace />;

  return (
    <EmptyState
      icon={ShieldX}
      title="You do not have access to this page"
      description="Ask the shop owner to grant the required permission on your role."
    />
  );
}

/** Inline version for hiding a button or a table column. */
export function PermissionGate({
  permission, children, fallback = null,
}: { permission: string; children: React.ReactNode; fallback?: React.ReactNode }) {
  const { hasPermission } = useAuth();
  return <>{hasPermission(permission) ? children : fallback}</>;
}
