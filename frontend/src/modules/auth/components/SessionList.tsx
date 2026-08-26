import { useCallback, useEffect, useState } from 'react';
import { Laptop, Loader2, Smartphone } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { EmptyState } from '@/shared/components/EmptyState';
import { formatDateTime, formatRelative } from '@/shared/lib/utils';
import { authService } from '../services/authService';
import { useToast } from '../hooks/useToast';
import type { SessionResponse } from '../types';

/** Crude but useful: enough to tell a phone from a counter terminal. */
function isMobileAgent(userAgent?: string | null): boolean {
  return /android|iphone|ipad|mobile/i.test(userAgent ?? '');
}

function deviceLabel(userAgent?: string | null): string {
  if (!userAgent) return 'Unknown device';
  if (/android/i.test(userAgent)) return 'Android';
  if (/iphone|ipad/i.test(userAgent)) return 'iPhone or iPad';
  if (/windows/i.test(userAgent)) return 'Windows';
  if (/macintosh|mac os/i.test(userAgent)) return 'macOS';
  if (/linux/i.test(userAgent)) return 'Linux';
  return 'Unknown device';
}

export function SessionList() {
  const [sessions, setSessions] = useState<SessionResponse[] | null>(null);
  const [revoking, setRevoking] = useState<number | null>(null);
  const toast = useToast();

  const load = useCallback(async () => {
    try {
      setSessions(await authService.sessions());
    } catch (error) {
      toast.error(error, 'Could not load your sessions.');
      setSessions([]);
    }
    // toast is stable; excluding it keeps this from re-running every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  const revoke = async (session: SessionResponse) => {
    setRevoking(session.id);
    try {
      await authService.revokeSession(session.id);
      toast.success('Session signed out.');
      await load();
    } catch (error) {
      toast.error(error, 'Could not sign that session out.');
    } finally {
      setRevoking(null);
    }
  };

  if (sessions === null) {
    return (
      <div className="flex justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  if (sessions.length === 0) {
    return <EmptyState icon={Laptop} title="No other active sessions" />;
  }

  return (
    <ul className="divide-y">
      {sessions.map((session) => {
        const Icon = isMobileAgent(session.userAgent) ? Smartphone : Laptop;
        return (
          <li key={session.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex min-w-0 items-start gap-3">
              <Icon className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                  {deviceLabel(session.userAgent)}
                  {session.current ? <Badge variant="success">This device</Badge> : null}
                </p>
                <p className="tabular mt-0.5 text-xs text-muted-foreground">
                  {session.ipAddress ?? 'Unknown IP'} · last used {formatRelative(session.lastUsedAt)}
                </p>
                <p className="tabular text-xs text-muted-foreground">
                  Signed in {formatDateTime(session.createdAt)}
                </p>
              </div>
            </div>

            {!session.current ? (
              <Button
                variant="outline" size="sm"
                onClick={() => revoke(session)}
                loading={revoking === session.id}
                className="self-start sm:self-auto"
              >
                Sign out
              </Button>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
