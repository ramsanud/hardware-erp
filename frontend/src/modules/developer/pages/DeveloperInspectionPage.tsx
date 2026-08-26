import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { Loader2, RefreshCw, ShieldX, TerminalSquare } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { ApiError } from '@/shared/types/api';
import { developerService } from '../services/developerService';
import type { DeveloperInspectionStatus, RequestEcho, RuntimeDiagnostics } from '../types';

/**
 * Developer inspection (CR-045).
 *
 * This page is convenience only. Every field on it comes from an endpoint the
 * server refuses unless the environment permits inspection AND the caller
 * holds DEVELOPER_INSPECT, so reaching the page by typing the URL, or by
 * editing the bundle in a browser, shows exactly the same nothing.
 *
 * It deliberately implements no defence against browser DevTools. Blocking
 * F12, right-click or Ctrl+Shift+I would inconvenience the honest and stop
 * nobody; the real boundary is the API.
 */
export function DeveloperInspectionPage() {
  const [status, setStatus] = useState<DeveloperInspectionStatus | null>(null);
  const [runtime, setRuntime] = useState<RuntimeDiagnostics | null>(null);
  const [echo, setEcho] = useState<RequestEcho | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      // Status first: it is the one call an ordinary user is allowed to make,
      // so it can explain a refusal instead of leaving a bare 403 on screen.
      const current = await developerService.status();
      setStatus(current);

      if (current.available) {
        const [diagnostics, request] = await Promise.all([
          developerService.runtime(),
          developerService.requestEcho(),
        ]);
        setRuntime(diagnostics);
        setEcho(request);
      } else {
        setRuntime(null);
        setEcho(null);
      }
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({
          message: 'Could not load developer diagnostics',
          code: 'INTERNAL_ERROR',
          status: 500,
        }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const formatUptime = (seconds: number) => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m ${seconds % 60}s`;
  };

  return (
    <>
      <PageHeader
        title="Developer inspection"
        description="Diagnostics for this instance. Not available in production, and not part of the ERP."
        actions={(
          <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={loading ? 'mr-2 h-4 w-4 animate-spin' : 'mr-2 h-4 w-4'} />
            Refresh
          </Button>
        )}
      />

      {error ? (
        <div className="mt-6">
          <ErrorState error={error} onRetry={() => void load()} />
        </div>
      ) : null}

      {loading && !status ? (
        <div className="mt-10 flex justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
        </div>
      ) : null}

      {status && !status.available ? (
        <div className="mt-6">
          <EmptyState
            icon={ShieldX}
            title="Developer inspection is not available here"
            description={status.environmentAllows
              ? 'This environment permits inspection, but your role does not hold the '
                + 'DEVELOPER_INSPECT permission. Ask the shop owner to grant it on the Roles screen.'
              : `Diagnostics are switched off in this environment (${status.activeProfiles.join(', ') || 'default'}). `
                + 'Production always refuses, whatever the configuration says.'}
          />
        </div>
      ) : null}

      {status?.available ? (
        <div className="mt-6 grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <TerminalSquare className="h-4 w-4" />
                Runtime
              </CardTitle>
              <CardDescription>
                A fixed list of named fields. Never a system-property or environment dump.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {runtime ? (
                <dl className="grid gap-x-4 gap-y-3 text-sm sm:grid-cols-2">
                  <Fact label="Application" value={runtime.application} />
                  <Fact label="Version" value={runtime.version} />
                  <Fact
                    label="Profiles"
                    value={(
                      <span className="flex flex-wrap gap-1">
                        {(runtime.activeProfiles.length ? runtime.activeProfiles : ['default'])
                          .map((profile) => (
                            <Badge key={profile} variant="secondary">{profile}</Badge>
                          ))}
                      </span>
                    )}
                  />
                  <Fact label="Java" value={runtime.javaVersion} />
                  <Fact label="OS" value={runtime.osName} />
                  <Fact label="CPUs" value={String(runtime.availableProcessors)} />
                  <Fact label="Heap" value={`${runtime.heapUsedMb} MB / ${runtime.heapMaxMb} MB`} />
                  <Fact label="Uptime" value={formatUptime(runtime.uptimeSeconds)} />
                  <Fact label="Server time" value={runtime.serverTime} />
                  <Fact label="Time zone" value={runtime.serverTimeZone} />
                </dl>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">This request, as the server saw it</CardTitle>
              <CardDescription>
                For diagnosing a reverse proxy that rewrites headers, and for confirming which
                tenant your token resolved to. Credential headers are removed server-side.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {echo ? (
                <>
                  <dl className="grid gap-x-4 gap-y-3 text-sm sm:grid-cols-2">
                    <Fact label="Method" value={echo.method} />
                    <Fact label="Path" value={echo.path} />
                    <Fact label="Request ID" value={echo.requestId ?? '-'} />
                    <Fact label="Client IP" value={echo.clientIp ?? '-'} />
                    <Fact label="User ID" value={echo.userId?.toString() ?? '-'} />
                    <Fact label="Tenant ID" value={echo.tenantId?.toString() ?? '-'} />
                  </dl>
                  <div className="mt-4">
                    <p className="mb-2 text-sm font-medium">Headers</p>
                    {/* A long header value scrolls inside this box rather than
                        widening the page on a phone. */}
                    <div className="overflow-x-auto rounded-md border">
                      <table className="w-full text-xs">
                        <tbody>
                          {Object.entries(echo.headers).map(([name, value]) => (
                            <tr key={name} className="border-b last:border-0">
                              <td className="whitespace-nowrap px-3 py-2 font-medium text-muted-foreground">
                                {name}
                              </td>
                              <td className="break-all px-3 py-2 font-mono">{value}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </>
              ) : null}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </>
  );
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 break-words font-medium">{value}</dd>
    </div>
  );
}
