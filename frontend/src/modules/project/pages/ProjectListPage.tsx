import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  ColumnSettings, useColumnPreferences, type ColumnDef,
} from '@/shared/components/table/ColumnPreferences';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { PROJECT_ROUTES, PROJECT_STATUS_OPTIONS } from '../constants';
import { projectService } from '../services/projectService';
import { ProjectStatusBadge, ProjectOutcomeBadge } from '../components/ProjectStatusBadge';
import type { ProjectStatus, ProjectSummaryResponse } from '../types';

/** CR-068. Column catalogue - ids are persisted, so never rename them. */
const PROJECT_COLUMNS: ColumnDef<ProjectSummaryResponse>[] = [
  {
    id: 'project',
    header: 'Project',
    locked: true,
    cell: (row) => (
      <>
        <span className="font-medium">{row.projectName}</span>
        <span className="tabular mt-0.5 block text-xs text-muted-foreground">{row.projectNumber}</span>
      </>
    ),
  },
  {
    id: 'customer',
    header: 'Customer',
    headClassName: 'hidden sm:table-cell',
    cellClassName: 'hidden sm:table-cell',
    cell: (row) => row.customerName,
  },
  {
    id: 'workType',
    header: 'Work type',
    headClassName: 'hidden md:table-cell',
    cellClassName: 'hidden md:table-cell',
    cell: (row) => row.workTypeName,
  },
  {
    id: 'value',
    header: 'Value',
    headClassName: 'hidden lg:table-cell',
    cellClassName: 'tabular hidden lg:table-cell',
    cell: (row) => <>&#8377;{row.projectValueDisplay}</>,
  },
  {
    id: 'profit',
    header: 'Profit',
    headClassName: 'hidden lg:table-cell',
    cell: (row) => (
      <span className={row.profitPositive ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'}>
        {row.profitPositive ? '+' : '-'}&#8377;{row.netProfitDisplay}
      </span>
    ),
    cellClassName: 'tabular hidden lg:table-cell',
  },
  {
    id: 'status',
    header: 'Status',
    cellClassName: 'status-on-card',
    cell: (row) => (
      <div className="flex flex-col gap-1">
        <ProjectStatusBadge status={row.status} overdue={row.overdue} />
        {row.outcome ? <ProjectOutcomeBadge outcome={row.outcome} /> : null}
      </div>
    ),
  },
];

const ALL = '__all__';

export function ProjectListPage() {
  const navigate = useNavigate();
  const columns = useColumnPreferences('project', PROJECT_COLUMNS);

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, size]);

  const fetcher = useCallback(
    () => projectService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as ProjectStatus),
      page,
      size,
    }),
    [debouncedSearch, status, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, page, size]);

  return (
    <>
      <PageHeader
        title="Projects"
        description="Modular kitchens, fabrication, roofing and other custom work for your customers."
        actions={
          <div className="flex items-center gap-2">
            <ColumnSettings preferences={columns} label="project" />
            <PermissionGate permission={PERMISSIONS.PROJECT_MANAGE}>
              <Button onClick={() => navigate(PROJECT_ROUTES.create)}>
                <Plus className="h-4 w-4" />
                <span>New project</span>
              </Button>
            </PermissionGate>
          </div>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Project name, number or customer…" />

        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {PROJECT_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
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
                    <TableRow key={row.id} className="cursor-pointer" onClick={() => navigate(PROJECT_ROUTES.detail(row.id))}>
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
                icon={ClipboardList}
                title="No projects match these filters"
                description="Try clearing the search box or the status filter."
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
