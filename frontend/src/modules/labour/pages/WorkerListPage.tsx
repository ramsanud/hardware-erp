import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MoreHorizontal, Pencil, Plus, UserCheck, UserX } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/shared/components/ui/dialog';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/shared/components/ui/dropdown-menu';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { LABOUR_ROUTES, WORKER_STATUS_OPTIONS } from '../constants';
import { workerService } from '../services/workerService';
import { WorkerForm } from '../forms/WorkerForm';
import { WorkerStatusBadge } from '../components/WorkerStatusBadge';
import type { WorkerResponse, WorkerStatus } from '../types';
import type { WorkerValues } from '../validation/schemas';

const ALL = '__all__';

export function WorkerListPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<WorkerResponse | null>(null);
  const [deactivating, setDeactivating] = useState<WorkerResponse | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, size]);

  const fetcher = useCallback(
    () => workerService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as WorkerStatus),
      page,
      size,
    }),
    [debouncedSearch, status, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, page, size]);

  const toRequest = (values: WorkerValues) => ({
    name: values.name,
    mobileNo: values.mobileNo || null,
    roleTitle: values.roleTitle || null,
    dailyRatePaise: Math.round(Number(values.dailyRateRupees) * 100),
  });

  const handleCreate = async (values: WorkerValues) => {
    await workerService.create(toRequest(values));
    setCreating(false);
    toast.success('Worker added.');
    await reload();
  };

  const handleUpdate = async (values: WorkerValues) => {
    if (!editing) return;
    await workerService.update(editing.id, toRequest(values));
    setEditing(null);
    toast.success('Worker updated.');
    await reload();
  };

  const handleDeactivate = async () => {
    if (!deactivating) return;
    try {
      await workerService.deactivate(deactivating.id);
      toast.success('Worker deactivated.');
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not deactivate this worker.');
      throw caught;
    }
  };

  const handleActivate = async (worker: WorkerResponse) => {
    try {
      await workerService.activate(worker.id);
      toast.success('Worker reactivated.');
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not reactivate this worker.');
    }
  };

  return (
    <>
      <PageHeader
        title="Workers"
        description="The shop's own day-wage labour force - separate from suppliers and customers."
        actions={
          <PermissionGate permission={PERMISSIONS.LABOUR_MANAGE}>
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" />
              <span className="hidden sm:inline">Add worker</span>
            </Button>
          </PermissionGate>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Search name, mobile number…" />

        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {WORKER_STATUS_OPTIONS.map((option) => (
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
                  <TableHead>Name</TableHead>
                  <TableHead className="hidden sm:table-cell">Role</TableHead>
                  <TableHead className="hidden md:table-cell">Mobile</TableHead>
                  <TableHead className="text-right">Daily rate</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={6} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id} className="cursor-pointer"
                              onClick={() => navigate(LABOUR_ROUTES.workerDetail(row.id))}>
                      <TableCell className="font-medium">{row.name}</TableCell>
                      <TableCell className="hidden sm:table-cell">{row.roleTitle ?? '—'}</TableCell>
                      <TableCell className="hidden md:table-cell">{row.mobileNo ?? '—'}</TableCell>
                      <TableCell className="tabular text-right">₹{row.dailyRateDisplay}</TableCell>
                      <TableCell><WorkerStatusBadge status={row.status} /></TableCell>
                      <TableCell onClick={(e) => e.stopPropagation()}>
                        <PermissionGate permission={PERMISSIONS.LABOUR_MANAGE}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Actions for this worker">
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={() => setEditing(row)}>
                                <Pencil className="h-4 w-4" />
                                Edit
                              </DropdownMenuItem>
                              {row.status === 'ACTIVE' ? (
                                <DropdownMenuItem destructive onClick={() => setDeactivating(row)}>
                                  <UserX className="h-4 w-4" />
                                  Deactivate
                                </DropdownMenuItem>
                              ) : (
                                <DropdownMenuItem onClick={() => handleActivate(row)}>
                                  <UserCheck className="h-4 w-4" />
                                  Reactivate
                                </DropdownMenuItem>
                              )}
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </PermissionGate>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={UserCheck}
                title="No workers match these filters"
                description="Try clearing the search box or status filter."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={creating} onOpenChange={(open) => !open && setCreating(false)}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader><DialogTitle>Add worker</DialogTitle></DialogHeader>
          <WorkerForm onSubmit={handleCreate} onCancel={() => setCreating(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader><DialogTitle>Edit worker</DialogTitle></DialogHeader>
          {editing ? <WorkerForm worker={editing} onSubmit={handleUpdate} onCancel={() => setEditing(null)} /> : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deactivating !== null}
        onOpenChange={(open) => !open && setDeactivating(null)}
        title="Deactivate this worker?"
        description="Past attendance and payment history stay intact, but the worker no longer appears when marking new attendance."
        confirmLabel="Deactivate"
        destructive
        onConfirm={handleDeactivate}
      />
    </>
  );
}
