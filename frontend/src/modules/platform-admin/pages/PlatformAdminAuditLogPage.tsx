import { useCallback, useEffect, useState } from 'react';
import { FileClock } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
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
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { platformAdminAuditLogService } from '../services/platformAdminAuditLogService';

const ALL = '__all__';

export function PlatformAdminAuditLogPage() {
  const [action, setAction] = useState('');
  const [targetType, setTargetType] = useState('');
  const [success, setSuccess] = useState<string>(ALL);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const debouncedAction = useDebouncedValue(action, SEARCH_DEBOUNCE_MS);
  const debouncedTargetType = useDebouncedValue(targetType, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedAction, debouncedTargetType, success, fromDate, toDate, size]);

  const fetcher = useCallback(
    () => platformAdminAuditLogService.search({
      action: debouncedAction.trim() ? debouncedAction.trim().toUpperCase() : undefined,
      targetType: debouncedTargetType.trim() ? debouncedTargetType.trim().toUpperCase() : undefined,
      success: success === ALL ? undefined : success === 'true',
      fromDate: fromDate ? `${fromDate}T00:00:00` : undefined,
      toDate: toDate ? `${toDate}T23:59:59` : undefined,
      page,
      size,
    }),
    [debouncedAction, debouncedTargetType, success, fromDate, toDate, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(
    fetcher, [debouncedAction, debouncedTargetType, success, fromDate, toDate, page, size],
  );

  return (
    <>
      <PageHeader title="Global Audit Log" description="Every privileged platform-admin action, append-only." />

      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <Input placeholder="Action (e.g. TENANT_SUSPENDED)" className="sm:w-56" value={action} onChange={(e) => setAction(e.target.value)} />
        <Input placeholder="Target type (e.g. TENANT)" className="sm:w-44" value={targetType} onChange={(e) => setTargetType(e.target.value)} />
        <Select value={success} onValueChange={setSuccess}>
          <SelectTrigger className="sm:w-36"><SelectValue placeholder="Result" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All results</SelectItem>
            <SelectItem value="true">Success</SelectItem>
            <SelectItem value="false">Failure</SelectItem>
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
                    <TableHead>Timestamp</TableHead>
                    <TableHead>Admin</TableHead>
                    <TableHead>Action</TableHead>
                    <TableHead>Target</TableHead>
                    <TableHead>Result</TableHead>
                    <TableHead className="hidden lg:table-cell">Detail</TableHead>
                    <TableHead className="hidden xl:table-cell">IP</TableHead>
                  </TableRow>
                </TableHeader>

                {loading ? (
                  <TableSkeleton columns={7} rows={size > 10 ? 10 : 6} />
                ) : (
                  <TableBody>
                    {data?.content.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell className="whitespace-nowrap text-xs text-muted-foreground">{formatDateTime(row.createdAt)}</TableCell>
                        <TableCell className="text-xs">{row.adminEmail ?? <span className="text-muted-foreground">System</span>}</TableCell>
                        <TableCell className="text-xs font-medium">{row.action}</TableCell>
                        <TableCell className="text-xs text-muted-foreground">
                          {row.targetType ? `${row.targetType}${row.targetId ? ` #${row.targetId}` : ''}` : '—'}
                        </TableCell>
                        <TableCell>
                          <Badge variant={row.success ? 'default' : 'destructive'}>{row.success ? 'Success' : 'Failure'}</Badge>
                        </TableCell>
                        <TableCell className="hidden lg:table-cell max-w-xs truncate text-xs text-muted-foreground">{row.detail ?? '—'}</TableCell>
                        <TableCell className="hidden xl:table-cell text-xs text-muted-foreground">{row.ipAddress ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>
            </div>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState icon={FileClock} title="No audit events match these filters" description="Every privileged action across the platform is recorded here." />
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
