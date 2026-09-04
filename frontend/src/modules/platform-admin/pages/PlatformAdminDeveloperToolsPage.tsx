import { useEffect, useState } from 'react';
import { CheckCircle2, Database, Loader2, RefreshCw, Timer, XCircle } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { formatDateTime } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { platformAdminDeveloperToolsService } from '../services/platformAdminDeveloperToolsService';
import type { BackgroundJobResponse, DatabaseDiagnosticsResponse } from '../types';

export function PlatformAdminDeveloperToolsPage() {
  const toast = useToast();
  const { admin } = usePlatformAdminAuth();
  const canManage = admin?.permissions.includes('DEVELOPER_TOOLS_MANAGE') ?? false;

  const [jobs, setJobs] = useState<BackgroundJobResponse[] | null>(null);
  const [db, setDb] = useState<DatabaseDiagnosticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [retrying, setRetrying] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [jobsResult, dbResult] = await Promise.all([
        platformAdminDeveloperToolsService.jobs(),
        platformAdminDeveloperToolsService.database(),
      ]);
      setJobs(jobsResult);
      setDb(dbResult);
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const retry = async (jobName: string) => {
    setRetrying(jobName);
    try {
      await platformAdminDeveloperToolsService.retryJob(jobName);
      toast.success(`${jobName} retried.`);
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not retry this job.');
    } finally {
      setRetrying(null);
    }
  };

  if (error) return <Card><ErrorState error={error} onRetry={load} /></Card>;
  if (loading || !jobs || !db) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <PageHeader title="Developer Tools" description="Real diagnostics only - no arbitrary SQL execution exists here." />

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Database className="h-4 w-4" />Database</CardTitle></CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Row label="Connection" value={db.connectionReachable ? 'Reachable' : 'Unreachable'} badge={db.connectionReachable ? 'default' : 'destructive'} />
            <Row label="Ping" value={db.pingMs !== null ? `${db.pingMs} ms` : '—'} />
            {db.pool ? (
              <>
                <Row label="Pool - active" value={String(db.pool.active)} />
                <Row label="Pool - idle" value={String(db.pool.idle)} />
                <Row label="Pool - max size" value={String(db.pool.maxSize)} />
              </>
            ) : null}
            <Row label="Migration version" value={db.migrationVersion ?? '—'} />
            <Row label="Applied migrations" value={String(db.appliedMigrationCount)} />
            <Row label="Pending migrations" value={db.migrationsPending ? 'Yes' : 'No'} badge={db.migrationsPending ? 'destructive' : undefined} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Timer className="h-4 w-4" />Cache</CardTitle></CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            No application-level cache layer (Redis/Caffeine/@Cacheable) is configured in this application -
            reported honestly rather than showing a fabricated status.
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle className="text-base">Background Jobs</CardTitle></CardHeader>
        <CardContent className="px-0 pb-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Job</TableHead>
                <TableHead>Last status</TableHead>
                <TableHead className="hidden sm:table-cell">Last run</TableHead>
                <TableHead className="hidden md:table-cell">Duration</TableHead>
                <TableHead className="hidden lg:table-cell">Detail</TableHead>
                {canManage ? <TableHead className="w-24" /> : null}
              </TableRow>
            </TableHeader>
            <TableBody>
              {jobs.map((job) => (
                <TableRow key={job.jobName}>
                  <TableCell className="font-medium">{job.jobName}</TableCell>
                  <TableCell>
                    <Badge variant={job.lastStatus === 'SUCCESS' ? 'default' : job.lastStatus === 'FAILED' ? 'destructive' : 'secondary'} className="gap-1">
                      {job.lastStatus === 'SUCCESS' ? <CheckCircle2 className="h-3 w-3" /> : job.lastStatus === 'FAILED' ? <XCircle className="h-3 w-3" /> : null}
                      {job.lastStatus}
                    </Badge>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell text-xs text-muted-foreground">{formatDateTime(job.lastRunAt)}</TableCell>
                  <TableCell className="hidden md:table-cell tabular text-xs text-muted-foreground">{job.lastDurationMs !== null ? `${job.lastDurationMs} ms` : '—'}</TableCell>
                  <TableCell className="hidden lg:table-cell max-w-xs truncate text-xs text-muted-foreground">{job.lastDetail ?? '—'}</TableCell>
                  {canManage ? (
                    <TableCell>
                      {job.retryable ? (
                        <Button variant="outline" size="sm" loading={retrying === job.jobName} onClick={() => void retry(job.jobName)}>
                          <RefreshCw className="h-3.5 w-3.5" />
                          Retry
                        </Button>
                      ) : null}
                    </TableCell>
                  ) : null}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </>
  );
}

function Row({ label, value, badge }: { label: string; value: string; badge?: 'default' | 'destructive' }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      {badge ? <Badge variant={badge}>{value}</Badge> : <span className="tabular">{value}</span>}
    </div>
  );
}
