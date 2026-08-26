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
import type { ProjectStatus } from '../types';

const ALL = '__all__';

export function ProjectListPage() {
  const navigate = useNavigate();

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
          <PermissionGate permission={PERMISSIONS.PROJECT_MANAGE}>
            <Button onClick={() => navigate(PROJECT_ROUTES.create)}>
              <Plus className="h-4 w-4" />
              <span className="hidden sm:inline">New project</span>
            </Button>
          </PermissionGate>
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
                  <TableHead>Project</TableHead>
                  <TableHead className="hidden sm:table-cell">Customer</TableHead>
                  <TableHead className="hidden md:table-cell">Work type</TableHead>
                  <TableHead className="hidden lg:table-cell">Value</TableHead>
                  <TableHead className="hidden lg:table-cell">Profit</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={6} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id} className="cursor-pointer" onClick={() => navigate(PROJECT_ROUTES.detail(row.id))}>
                      <TableCell>
                        <span className="font-medium">{row.projectName}</span>
                        <span className="tabular mt-0.5 block text-xs text-muted-foreground">{row.projectNumber}</span>
                      </TableCell>
                      <TableCell className="hidden sm:table-cell">{row.customerName}</TableCell>
                      <TableCell className="hidden md:table-cell">{row.workTypeName}</TableCell>
                      <TableCell className="tabular hidden lg:table-cell">₹{row.projectValueDisplay}</TableCell>
                      <TableCell className={`tabular hidden lg:table-cell ${row.profitPositive ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'}`}>
                        {row.profitPositive ? '+' : '-'}₹{row.netProfitDisplay}
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col gap-1">
                          <ProjectStatusBadge status={row.status} overdue={row.overdue} />
                          {row.outcome ? <ProjectOutcomeBadge outcome={row.outcome} /> : null}
                        </div>
                      </TableCell>
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
