import { useEffect, useState } from 'react';
import { Loader2, LogOut, ShieldAlert } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { formatDateTime } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { platformAdminSecurityService } from '../services/platformAdminSecurityService';
import type { PlatformAdminActiveSessionResponse, PlatformSecurityDashboardResponse } from '../types';

export function PlatformAdminSecurityPage() {
  const toast = useToast();
  const [dashboard, setDashboard] = useState<PlatformSecurityDashboardResponse | null>(null);
  const [sessions, setSessions] = useState<PlatformAdminActiveSessionResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [revokingId, setRevokingId] = useState<number | null>(null);
  const [confirmRevokeOthers, setConfirmRevokeOthers] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [dashboardResult, sessionsResult] = await Promise.all([
        platformAdminSecurityService.dashboard().catch(() => null),
        platformAdminSecurityService.mySessions(),
      ]);
      setDashboard(dashboardResult);
      setSessions(sessionsResult);
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const revoke = async (id: number) => {
    setRevokingId(id);
    try {
      await platformAdminSecurityService.revokeSession(id);
      toast.success('Session signed out.');
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not revoke this session.');
    } finally {
      setRevokingId(null);
    }
  };

  const revokeOthers = async () => {
    const count = await platformAdminSecurityService.revokeOtherSessions();
    toast.success(count > 0 ? `${count} other session(s) signed out.` : 'No other sessions to sign out.');
    await load();
  };

  if (error) return <Card><ErrorState error={error} onRetry={load} /></Card>;
  if (loading || !sessions) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <PageHeader title="Security Center" description="Login security, active sessions and recent privileged actions." />

      {dashboard ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatTile label="Failed logins today" value={dashboard.failedLoginsToday} tone={dashboard.failedLoginsToday > 0 ? 'danger' : undefined} />
          <StatTile label="MFA failures today" value={dashboard.mfaChallengeFailuresToday} tone={dashboard.mfaChallengeFailuresToday > 0 ? 'danger' : undefined} />
          <StatTile label="Lockouts today" value={dashboard.accountsLockedToday} tone={dashboard.accountsLockedToday > 0 ? 'danger' : undefined} />
          <StatTile label="Total admins" value={dashboard.totalAdmins} />
          <StatTile label="MFA enabled" value={dashboard.adminsWithMfaEnabled} tone="success" />
          <StatTile label="Active sessions" value={dashboard.activeSessions} />
        </div>
      ) : null}

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle className="text-base">Your sessions</CardTitle>
          {sessions.length > 1 ? (
            <Button variant="outline" size="sm" onClick={() => setConfirmRevokeOthers(true)}>
              <LogOut className="h-3.5 w-3.5" />
              Sign out other sessions
            </Button>
          ) : null}
        </CardHeader>
        <CardContent className="px-0 pb-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Device / Browser</TableHead>
                <TableHead className="hidden sm:table-cell">IP</TableHead>
                <TableHead className="hidden md:table-cell">Created</TableHead>
                <TableHead className="hidden lg:table-cell">Last active</TableHead>
                <TableHead className="w-28" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {sessions.map((session) => (
                <TableRow key={session.id}>
                  <TableCell className="max-w-xs truncate text-sm">
                    {session.userAgent ?? 'Unknown device'}
                    {session.current ? <Badge variant="outline" className="ml-2 text-[10px]">This session</Badge> : null}
                  </TableCell>
                  <TableCell className="hidden sm:table-cell text-xs text-muted-foreground">{session.ipAddress ?? '—'}</TableCell>
                  <TableCell className="hidden md:table-cell text-xs text-muted-foreground">{formatDateTime(session.createdAt)}</TableCell>
                  <TableCell className="hidden lg:table-cell text-xs text-muted-foreground">
                    {session.lastUsedAt ? formatDateTime(session.lastUsedAt) : 'Never'}
                  </TableCell>
                  <TableCell>
                    {!session.current ? (
                      <Button variant="ghost" size="sm" loading={revokingId === session.id} onClick={() => void revoke(session.id)}>
                        Revoke
                      </Button>
                    ) : null}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {dashboard && dashboard.recentPrivilegedActions.length > 0 ? (
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><ShieldAlert className="h-4 w-4" />Recent privileged actions</CardTitle></CardHeader>
          <CardContent className="px-0 pb-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Timestamp</TableHead>
                  <TableHead>Admin</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Result</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {dashboard.recentPrivilegedActions.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="text-xs text-muted-foreground">{formatDateTime(entry.createdAt)}</TableCell>
                    <TableCell className="text-xs">{entry.adminEmail ?? 'System'}</TableCell>
                    <TableCell className="text-xs font-medium">{entry.action}</TableCell>
                    <TableCell><Badge variant={entry.success ? 'default' : 'destructive'}>{entry.success ? 'Success' : 'Failure'}</Badge></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      <ConfirmDialog
        open={confirmRevokeOthers}
        onOpenChange={setConfirmRevokeOthers}
        title="Sign out all other sessions?"
        description="Every other device signed in as you will be signed out immediately. This session stays active."
        confirmLabel="Sign out others"
        destructive
        onConfirm={revokeOthers}
      />
    </>
  );
}

function StatTile({ label, value, tone }: { label: string; value: number; tone?: 'success' | 'danger' }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className={`tabular text-2xl font-semibold ${tone === 'success' ? 'text-success' : tone === 'danger' ? 'text-destructive' : ''}`}>{value}</p>
        <p className="text-xs text-muted-foreground">{label}</p>
      </CardContent>
    </Card>
  );
}
