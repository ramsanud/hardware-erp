import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { History } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card } from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  ColumnSettings, useColumnPreferences, type ColumnDef,
} from '@/shared/components/table/ColumnPreferences';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { activityLogService } from '../services/activityLogService';
import type { ActivityAction, ActivityLogResponse } from '../types';

const ALL = '__all__';

const ACTION_VARIANT: Record<ActivityAction, 'success' | 'warning' | 'destructive' | 'secondary'> = {
  CREATE: 'success',
  UPDATE: 'warning',
  DELETE: 'destructive',
  IMPORT: 'secondary',
};

/** CR-068 column catalogue - ids are persisted, so never rename them. */
const ACTIVITY_COLUMNS: ColumnDef<ActivityLogResponse>[] = [
  {
    id: 'record',
    header: 'Record',
    locked: true,
    cell: (row) => (
      <>
        <span className="font-medium">{row.entityLabel ?? row.entityType}</span>
        <span className="mt-0.5 block text-xs text-muted-foreground">
          {row.entityType}
          {row.entityId != null ? ` #${row.entityId}` : ''}
        </span>
      </>
    ),
  },
  {
    id: 'action',
    header: 'Action',
    cellClassName: 'status-on-card',
    cell: (row) => <Badge variant={ACTION_VARIANT[row.action] ?? 'secondary'}>{row.action}</Badge>,
  },
  {
    id: 'module',
    header: 'Module',
    headClassName: 'hidden sm:table-cell',
    cellClassName: 'hidden sm:table-cell text-muted-foreground',
    cell: (row) => row.moduleCode,
  },
  {
    id: 'who',
    header: 'Changed by',
    headClassName: 'hidden md:table-cell',
    cellClassName: 'hidden md:table-cell',
    cell: (row) => (
      <>
        <span>{row.fullName ?? '—'}</span>
        {row.roleCode ? (
          <span className="mt-0.5 block text-xs text-muted-foreground">{row.roleCode}</span>
        ) : null}
      </>
    ),
  },
  {
    id: 'when',
    header: 'When',
    headClassName: 'hidden lg:table-cell',
    cellClassName: 'tabular hidden lg:table-cell text-sm text-muted-foreground',
    cell: (row) => formatDateTime(row.createdAt),
  },
];

function DetailRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b py-2 text-sm last:border-b-0">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-medium">{value}</span>
    </div>
  );
}

/**
 * Renders the before/after maps side by side.
 *
 * They hold only the fields that actually moved - ActivityLogServiceImpl diffs
 * them at write time - so this stays short by construction rather than by
 * truncation, which is the whole reason the table is worth reading.
 */
function ValueDiff({ before, after }: {
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
}) {
  const keys = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])];
  if (keys.length === 0) {
    return (
      <p className="py-2 text-sm text-muted-foreground">
        No field values were recorded for this entry.
      </p>
    );
  }

  const show = (value: unknown) => {
    if (value === null || value === undefined) return <span className="text-muted-foreground">—</span>;
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  };

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-xs text-muted-foreground">
            <th className="py-2 pr-3 font-medium">Field</th>
            <th className="py-2 pr-3 font-medium">Before</th>
            <th className="py-2 font-medium">After</th>
          </tr>
        </thead>
        <tbody>
          {keys.map((key) => (
            <tr key={key} className="border-b align-top last:border-b-0">
              <td className="py-2 pr-3 font-medium">{key}</td>
              <td className="break-all py-2 pr-3 text-muted-foreground">{show(before?.[key])}</td>
              <td className="break-all py-2">{show(after?.[key])}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * CR-072. The business audit trail, readable for the first time.
 *
 * Deliberately a separate screen from the Security log: that one answers "who
 * tried to get in", this one answers "who changed the price". CR-015 split the
 * two tables for exactly that reason, and folding them into one screen would
 * undo it - a security review would be wading through supplier edits again.
 */
export function ActivityLogPage() {
  const columns = useColumnPreferences('activityLog', ACTIVITY_COLUMNS);
  const [moduleCode, setModuleCode] = useState<string>(ALL);
  const [modules, setModules] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [viewing, setViewing] = useState<ActivityLogResponse | null>(null);

  useEffect(() => { setPage(0); }, [moduleCode, size]);

  // The filter offers only modules this shop actually has history for, so it
  // can never present a choice that returns an empty table.
  useEffect(() => {
    let cancelled = false;
    activityLogService.moduleCodes()
      // Array.isArray, not a truthiness check: an endpoint that answers with a
      // null body - an error envelope, a stub, a shape change - would
      // otherwise put null into state and take the whole page down at
      // modules.map(). The filter is a convenience; it must never be the
      // reason the log cannot be read.
      .then((codes) => { if (!cancelled) setModules(Array.isArray(codes) ? codes : []); })
      .catch(() => { /* the list still loads without the filter */ });
    return () => { cancelled = true; };
  }, []);

  const fetcher = useCallback(
    () => activityLogService.search({
      moduleCode: moduleCode === ALL ? undefined : moduleCode,
      page,
      size,
    }),
    [moduleCode, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [moduleCode, page, size]);

  return (
    <>
      <PageHeader
        title="Activity log"
        description="Every change made to this shop's records, and what the values were before and after."
        actions={<ColumnSettings preferences={columns} label="activity" />}
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Select value={moduleCode} onValueChange={setModuleCode}>
          <SelectTrigger className="sm:w-56"><SelectValue placeholder="Module" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All modules</SelectItem>
            {modules.map((code) => (
              <SelectItem key={code} value={code}>{code}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  {columns.visible.map((column) => (
                    <TableHead key={column.id} className={columns.resolveClassName(column, 'head')}>
                      {column.header}
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={columns.visible.length} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id} className="cursor-pointer" onClick={() => setViewing(row)}>
                      {columns.visible.map((column) => (
                        <TableCell key={column.id} className={columns.resolveClassName(column, 'cell')}>
                          {column.cell(row)}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={History}
                title="No changes recorded yet"
                description="Edits made to products, customers, invoices and the rest will appear here."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={viewing !== null} onOpenChange={(open) => !open && setViewing(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{viewing?.entityLabel ?? viewing?.entityType ?? 'Change'}</DialogTitle>
            <DialogDescription>
              What changed, who changed it, and where the request came from.
            </DialogDescription>
          </DialogHeader>

          {viewing ? (
            <div className="space-y-4">
              <div>
                <DetailRow
                  label="Action"
                  value={<Badge variant={ACTION_VARIANT[viewing.action] ?? 'secondary'}>{viewing.action}</Badge>}
                />
                <DetailRow label="Module" value={viewing.moduleCode} />
                <DetailRow
                  label="Record"
                  value={`${viewing.entityType}${viewing.entityId != null ? ` #${viewing.entityId}` : ''}`}
                />
                <DetailRow label="Changed by" value={viewing.fullName ?? '—'} />
                <DetailRow label="Role at the time" value={viewing.roleCode ?? '—'} />
                <DetailRow label="When" value={formatDateTime(viewing.createdAt)} />
                <DetailRow label="IP address" value={viewing.ipAddress ?? '—'} />
                <DetailRow
                  label="Request id"
                  value={<span className="font-mono text-xs">{viewing.requestId ?? '—'}</span>}
                />
                {viewing.remarks ? <DetailRow label="Remarks" value={viewing.remarks} /> : null}
              </div>

              <div>
                <h3 className="mb-1 text-sm font-medium">Values</h3>
                <ValueDiff before={viewing.oldValues} after={viewing.newValues} />
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
