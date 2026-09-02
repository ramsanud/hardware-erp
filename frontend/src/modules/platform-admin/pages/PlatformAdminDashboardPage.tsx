import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';

/**
 * Phase 1 stops here deliberately - a real identity, a real session, and
 * nothing pretending to be more than that. Section 37 of the Platform Admin
 * spec forbids fabricated metrics: tenant counts, revenue, health, and
 * every other KPI arrive with the phase that actually computes them.
 */
export function PlatformAdminDashboardPage() {
  const { admin, logout } = usePlatformAdminAuth();

  return (
    <div className="min-h-dvh bg-muted/30 p-6">
      <div className="mx-auto max-w-3xl space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold">Platform Admin Console</h1>
            <p className="text-sm text-muted-foreground">Signed in as {admin?.email}</p>
          </div>
          <Button variant="outline" onClick={() => void logout()}>Sign out</Button>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Your account</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Name</span>
              <span>{admin?.fullName}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Role</span>
              <Badge variant="secondary">{admin?.role}</Badge>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Two-factor authentication</span>
              <Badge variant={admin?.mfaEnabled ? 'default' : 'destructive'}>
                {admin?.mfaEnabled ? 'Enabled' : 'Not enabled'}
              </Badge>
            </div>
            <div className="flex flex-wrap justify-between gap-2">
              <span className="text-muted-foreground">Permissions</span>
              <div className="flex flex-wrap justify-end gap-1">
                {admin?.permissions.length
                  ? admin.permissions.map((p) => <Badge key={p} variant="outline">{p}</Badge>)
                  : <span className="text-muted-foreground">None yet for this role</span>}
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Not available yet</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Tenant management, support tools, security center and platform analytics
            arrive in later phases of the Platform Admin Console. This is Phase 1:
            identity and authentication only.
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
