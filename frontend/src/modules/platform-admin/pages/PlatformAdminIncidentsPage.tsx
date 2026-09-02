import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, MoreHorizontal } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { platformAdminIncidentService } from '../services/platformAdminSystemHealthService';
import type { IncidentSeverity, IncidentStatus, PlatformServiceName } from '../types';

const ALL = '__all__';

const SEVERITY_BADGE: Record<IncidentSeverity, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  LOW: 'outline', MEDIUM: 'secondary', HIGH: 'default', CRITICAL: 'destructive',
};

const STATUS_BADGE: Record<IncidentStatus, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  OPEN: 'destructive', INVESTIGATING: 'secondary', RESOLVED: 'default', IGNORED: 'outline',
};

export function PlatformAdminIncidentsPage() {
  const toast = useToast();
  const { admin } = usePlatformAdminAuth();
  const canManage = admin?.permissions.includes('INCIDENT_MANAGE') ?? false;

  const [service, setService] = useState<string>(ALL);
  const [status, setStatus] = useState<string>(ALL);
  const [severity, setSeverity] = useState<string>(ALL);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  useEffect(() => { setPage(0); }, [service, status, severity, fromDate, toDate, size]);

  const fetcher = useCallback(
    () => platformAdminIncidentService.list({
      service: service === ALL ? undefined : (service as PlatformServiceName),
      status: status === ALL ? undefined : (status as IncidentStatus),
      severity: severity === ALL ? undefined : (severity as IncidentSeverity),
      fromDate: fromDate ? `${fromDate}T00:00:00` : undefined,
      toDate: toDate ? `${toDate}T23:59:59` : undefined,
      page,
      size,
    }),
    [service, status, severity, fromDate, toDate, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(
    fetcher, [service, status, severity, fromDate, toDate, page, size],
  );

  const act = async (action: 'investigating' | 'resolve' | 'ignore' | 'reopen', id: number) => {
    try {
      const fn = { investigating: platformAdminIncidentService.markInvestigating,
        resolve: platformAdminIncidentService.resolve,
        ignore: platformAdminIncidentService.ignore,
        reopen: platformAdminIncidentService.reopen }[action];
      await fn(id);
      toast.success('Incident updated.');
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not update this incident.');
    }
  };

  return (
    <>
      <PageHeader title="Incidents" description="Opened automatically by the system health checker, or investigated by hand." />

      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <Select value={service} onValueChange={setService}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Service" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All services</SelectItem>
            {['BACKEND', 'DATABASE', 'AUTHENTICATION', 'STORAGE', 'WHATSAPP', 'EMAIL', 'BACKGROUND_JOBS'].map((s) => (
              <SelectItem key={s} value={s}>{s}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {['OPEN', 'INVESTIGATING', 'RESOLVED', 'IGNORED'].map((s) => (
              <SelectItem key={s} value={s}>{s}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={severity} onValueChange={setSeverity}>
          <SelectTrigger className="sm:w-36"><SelectValue placeholder="Severity" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All severities</SelectItem>
            {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((s) => (
              <SelectItem key={s} value={s}>{s}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input type="date" className="sm:w-40" value={fromDate} onChange={(e) => setFromDate(e.target.value)} aria-label="From date" />
        <Input type="date" className="sm:w-40" value={toDate} onChange={(e) => setToDate(e.target.value)} aria-label="To date" />
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Title</TableHead>
                    <TableHead>Service</TableHead>
                    <TableHead>Severity</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="hidden sm:table-cell">Occurrences</TableHead>
                    <TableHead className="hidden md:table-cell">First seen</TableHead>
                    <TableHead className="hidden md:table-cell">Last seen</TableHead>
                    {canManage ? <TableHead className="w-12" /> : null}
                  </TableRow>
                </TableHeader>

                {loading ? (
                  <TableSkeleton columns={canManage ? 8 : 7} rows={size > 10 ? 8 : 5} />
                ) : (
                  <TableBody>
                    {data?.content.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>
                          <span className="font-medium">{row.title}</span>
                          {row.description ? (
                            <span className="mt-0.5 block max-w-xs truncate text-xs text-muted-foreground">{row.description}</span>
                          ) : null}
                        </TableCell>
                        <TableCell className="text-xs">{row.service}</TableCell>
                        <TableCell><Badge variant={SEVERITY_BADGE[row.severity]}>{row.severity}</Badge></TableCell>
                        <TableCell><Badge variant={STATUS_BADGE[row.status]}>{row.status}</Badge></TableCell>
                        <TableCell className="tabular hidden sm:table-cell">{row.occurrenceCount}</TableCell>
                        <TableCell className="hidden md:table-cell text-xs text-muted-foreground">{formatDateTime(row.firstSeen)}</TableCell>
                        <TableCell className="hidden md:table-cell text-xs text-muted-foreground">{formatDateTime(row.lastSeen)}</TableCell>
                        {canManage ? (
                          <TableCell>
                            <DropdownMenu>
                              <DropdownMenuTrigger asChild>
                                <button type="button" className="flex h-8 w-8 items-center justify-center rounded-md hover:bg-muted" aria-label={`Actions for ${row.title}`}>
                                  <MoreHorizontal className="h-4 w-4" />
                                </button>
                              </DropdownMenuTrigger>
                              <DropdownMenuContent align="end">
                                {row.status === 'OPEN' ? (
                                  <DropdownMenuItem onClick={() => act('investigating', row.id)}>Mark investigating</DropdownMenuItem>
                                ) : null}
                                {row.status !== 'RESOLVED' ? (
                                  <DropdownMenuItem onClick={() => act('resolve', row.id)}>Resolve</DropdownMenuItem>
                                ) : null}
                                {row.status === 'OPEN' || row.status === 'INVESTIGATING' ? (
                                  <DropdownMenuItem onClick={() => act('ignore', row.id)}>Ignore</DropdownMenuItem>
                                ) : null}
                                {row.status === 'RESOLVED' || row.status === 'IGNORED' ? (
                                  <DropdownMenuItem onClick={() => act('reopen', row.id)}>Reopen</DropdownMenuItem>
                                ) : null}
                              </DropdownMenuContent>
                            </DropdownMenu>
                          </TableCell>
                        ) : null}
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>
            </div>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={AlertTriangle}
                title="No incidents match these filters"
                description="When a health check fails, an incident opens here automatically."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>
    </>
  );
}
