import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { FileClock } from 'lucide-react';
import { Card } from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { securityAuditService } from '../services/securityAuditService';
import { AuditActionBadge } from '../components/AuditActionBadge';
import type { AuditAction, SecurityAuditLogResponse } from '../types';

const ALL = '__all__';

/** The actions worth filtering on during a security review. */
const FILTERABLE_ACTIONS: AuditAction[] = [
  'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED',
  'REFRESH_TOKEN_REUSE_DETECTED', 'RATE_LIMIT_EXCEEDED',
  'PASSWORD_CHANGED', 'PASSWORD_RESET', 'PASSWORD_RESET_BY_ADMIN',
  'USER_CREATED', 'USER_UPDATED', 'USER_DEACTIVATED', 'ROLE_CHANGED',
  'LOGOUT', 'LOGOUT_ALL', 'SESSION_REVOKED',
];

/** §24-26 - a click-through detail view. IP/user agent/request id previously existed only in the API response, never shown anywhere in the UI at all - not even IP, which the table only showed above the xl breakpoint. */
function DetailRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b py-2 text-sm last:border-b-0">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-medium">{value}</span>
    </div>
  );
}

export function SecurityAuditLogPage() {
  const [action, setAction] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [selected, setSelected] = useState<SecurityAuditLogResponse | null>(null);

  useEffect(() => { setPage(0); }, [action, size]);

  const fetcher = useCallback(
    () => securityAuditService.search({
      action: action === ALL ? undefined : (action as AuditAction),
      page,
      size,
      sortBy: 'createdAt',
      sortDir: 'desc',
    }),
    [action, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [action, page, size]);

  return (
    <>
      <PageHeader
        title="Security log"
        description="Sign-ins, password changes, role changes and token misuse. Business transaction history lives in each module."
      />

      <Select value={action} onValueChange={setAction}>
        <SelectTrigger className="sm:w-64"><SelectValue placeholder="All events" /></SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>All events</SelectItem>
          {FILTERABLE_ACTIONS.map((value) => (
            <SelectItem key={value} value={value}>
              {value.replaceAll('_', ' ').toLowerCase()}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>When</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead className="hidden md:table-cell">User</TableHead>
                  <TableHead className="hidden xl:table-cell">Source</TableHead>
                  <TableHead className="hidden lg:table-cell">Detail</TableHead>
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={5} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id} className="cursor-pointer hover:bg-accent/50" onClick={() => setSelected(row)}>
                      <TableCell className="tabular whitespace-nowrap text-sm">
                        {formatDateTime(row.createdAt)}
                      </TableCell>
                      <TableCell>
                        <AuditActionBadge action={row.action} success={row.success} />
                      </TableCell>
                      <TableCell className="hidden md:table-cell text-sm">
                        {row.fullName ?? <span className="text-muted-foreground">Unknown</span>}
                      </TableCell>
                      <TableCell className="tabular hidden xl:table-cell text-xs text-muted-foreground">
                        {row.ipAddress ?? '—'}
                      </TableCell>
                      <TableCell className="hidden lg:table-cell text-sm text-muted-foreground">
                        {row.failureReason ?? '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={FileClock}
                title="No security events match this filter"
                description="Records are never edited or deleted here, so an empty result means nothing matched."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={selected !== null} onOpenChange={(open) => { if (!open) setSelected(null); }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>Security event detail</DialogTitle></DialogHeader>
          {selected ? (
            <div>
              <DetailRow label="When" value={formatDateTime(selected.createdAt)} />
              <DetailRow label="Event" value={<AuditActionBadge action={selected.action} success={selected.success} />} />
              <DetailRow label="Actor" value={selected.fullName ?? <span className="text-muted-foreground">Unknown</span>} />
              <DetailRow label="Resource" value={
                selected.entityType
                  ? `${selected.entityType}${selected.entityId ? ` #${selected.entityId}` : ''}`
                  : '—'
              } />
              <DetailRow label="Status" value={selected.success ? 'Success' : 'Failure'} />
              {!selected.success && selected.failureReason ? (
                <DetailRow label="Reason" value={selected.failureReason} />
              ) : null}
              <DetailRow label="IP address" value={selected.ipAddress ?? '—'} />
              <DetailRow label="User agent" value={
                <span className="max-w-[280px] break-words text-right text-xs">{selected.userAgent ?? '—'}</span>
              } />
              <DetailRow label="Request ID" value={
                <span className="font-mono text-xs">{selected.requestId ?? '—'}</span>
              } />
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
