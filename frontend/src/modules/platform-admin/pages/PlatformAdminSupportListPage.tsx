import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LifeBuoy } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent } from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { platformAdminSupportService } from '../services/platformAdminSupportService';
import type { PlatformSupportDashboardResponse, TicketPriority, TicketStatus } from '../types';

const ALL = '__all__';

const STATUS_BADGE: Record<TicketStatus, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  OPEN: 'destructive', IN_PROGRESS: 'secondary', WAITING_FOR_USER: 'outline', RESOLVED: 'default', CLOSED: 'outline',
};
const PRIORITY_BADGE: Record<TicketPriority, 'default' | 'destructive' | 'secondary' | 'outline'> = {
  LOW: 'outline', MEDIUM: 'secondary', HIGH: 'default', URGENT: 'destructive',
};

export function PlatformAdminSupportListPage() {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<PlatformSupportDashboardResponse | null>(null);

  useEffect(() => { void platformAdminSupportService.dashboard().then(setDashboard); }, []);

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [priority, setPriority] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, priority, size]);

  const fetcher = useCallback(
    () => platformAdminSupportService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as TicketStatus),
      priority: priority === ALL ? undefined : (priority as TicketPriority),
      page,
      size,
    }),
    [debouncedSearch, status, priority, page, size],
  );
  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, priority, page, size]);

  return (
    <>
      <PageHeader title="Support Center" description="Tickets raised by tenants across the whole platform." />

      {dashboard ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatTile label="Open" value={dashboard.open} />
          <StatTile label="In progress" value={dashboard.inProgress} />
          <StatTile label="Waiting on tenant" value={dashboard.waitingForUser} />
          <StatTile label="High/Urgent" value={dashboard.highPriorityOrUrgent} tone={dashboard.highPriorityOrUrgent > 0 ? 'danger' : undefined} />
          <StatTile label="Assigned to me" value={dashboard.assignedToMe} />
          <StatTile label="Resolved today" value={dashboard.resolvedToday} tone="success" />
        </div>
      ) : null}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Subject or tenant name…" />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {['OPEN', 'IN_PROGRESS', 'WAITING_FOR_USER', 'RESOLVED', 'CLOSED'].map((s) => (
              <SelectItem key={s} value={s}>{s.replace(/_/g, ' ')}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={priority} onValueChange={setPriority}>
          <SelectTrigger className="sm:w-36"><SelectValue placeholder="Priority" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All priorities</SelectItem>
            {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((p) => <SelectItem key={p} value={p}>{p}</SelectItem>)}
          </SelectContent>
        </Select>
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
                    <TableHead>Subject</TableHead>
                    <TableHead>Tenant</TableHead>
                    <TableHead>Priority</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="hidden md:table-cell">Created</TableHead>
                  </TableRow>
                </TableHeader>
                {loading ? (
                  <TableSkeleton columns={5} rows={size > 10 ? 8 : 5} />
                ) : (
                  <TableBody>
                    {data?.content.map((row) => (
                      <TableRow key={row.id} className="cursor-pointer" onClick={() => navigate(PLATFORM_ADMIN_ROUTES.supportDetail(row.id))}>
                        <TableCell className="font-medium">{row.subject}</TableCell>
                        <TableCell className="text-xs text-muted-foreground">{row.tenantName}</TableCell>
                        <TableCell><Badge variant={PRIORITY_BADGE[row.priority]}>{row.priority}</Badge></TableCell>
                        <TableCell><Badge variant={STATUS_BADGE[row.status]}>{row.status.replace(/_/g, ' ')}</Badge></TableCell>
                        <TableCell className="hidden md:table-cell text-xs text-muted-foreground">{formatDateTime(row.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>
            </div>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState icon={LifeBuoy} title="No support tickets" description="Tickets raised by any tenant will appear here." />
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
