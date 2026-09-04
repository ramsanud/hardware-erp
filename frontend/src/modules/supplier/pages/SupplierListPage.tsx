import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MoreHorizontal, Pencil, RotateCcw, Trash2, Truck, UserPlus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
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
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { useAsyncData } from '@/shared/hooks/useAsyncData';
import { emptyPage } from '@/shared/types/api';
import { formatDateTime } from '@/shared/lib/utils';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SUPPLIER_ROUTES, SUPPLIER_STATUS_OPTIONS } from '../constants';
import { supplierService } from '../services/supplierService';
import { SupplierStatusBadge } from '../components/SupplierStatusBadge';
import type { SupplierStatus, SupplierSummaryResponse } from '../types';

const ALL = '__all__';

/**
 * CR-058. Not a SupplierStatus: a deleted supplier's status column still says
 * INACTIVE, and the rows live behind their own endpoint because
 * @SQLRestriction hides them from the ordinary search. Treating "Deleted" as
 * a fourth status in the same dropdown is how the user thinks about it; the
 * page routes it to a different request.
 */
const DELETED = '__deleted__';

export function SupplierListPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { hasPermission } = useAuth();
  // The deleted list and restore both require SUPPLIER_MANAGE server-side, so
  // a SUPPLIER_VIEW-only user is never offered the filter that would 403.
  const canManage = hasPermission(PERMISSIONS.SUPPLIER_MANAGE);

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [city, setCity] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [cities, setCities] = useState<string[]>([]);
  const [deleting, setDeleting] = useState<SupplierSummaryResponse | null>(null);
  const [restoringId, setRestoringId] = useState<number | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);
  const deletedMode = status === DELETED;

  // A filter change must return to page 0, or a search matching two rows on
  // page 4 shows an empty table.
  useEffect(() => { setPage(0); }, [debouncedSearch, status, city, size]);

  const fetcher = useCallback(
    // In deleted mode the ordinary search would be both wrong (it cannot see
    // deleted rows at all) and wasted, so it is never sent.
    () => (deletedMode
      ? Promise.resolve(emptyPage<SupplierSummaryResponse>(size))
      : supplierService.search({
        search: debouncedSearch || undefined,
        status: status === ALL ? undefined : (status as SupplierStatus),
        city: city === ALL ? undefined : city,
        page,
        size,
        sortBy: 'supplierName',
        sortDir: 'asc',
      })),
    [deletedMode, debouncedSearch, status, city, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher,
    [deletedMode, debouncedSearch, status, city, page, size]);

  const deletedFetcher = useCallback(
    () => (deletedMode ? supplierService.listDeleted() : Promise.resolve([])),
    [deletedMode],
  );
  const {
    data: deletedRows, loading: deletedLoading, error: deletedError, reload: reloadDeleted,
  } = useAsyncData(deletedFetcher, [deletedMode]);

  const handleRestore = async (id: number, name: string) => {
    setRestoringId(id);
    try {
      await supplierService.restore(id);
      toast.success(`${name} has been restored.`);
      await reloadDeleted();
    } catch (caught) {
      toast.error(caught, 'Could not restore this supplier.');
    } finally {
      setRestoringId(null);
    }
  };

  useEffect(() => {
    void (async () => {
      try {
        setCities(await supplierService.cities());
      } catch {
        // Cities only populate the filter dropdown; a failure here must not
        // block the supplier list, which is the point of the page.
        setCities([]);
      }
    })();
  }, []);

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await supplierService.remove(deleting.id);
      toast.success(`${deleting.supplierName} has been deactivated.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not deactivate this supplier.');
      throw caught;
    }
  };

  return (
    <>
      <PageHeader
        title="Suppliers"
        description="Businesses the shop buys from."
        actions={
          <PermissionGate permission={PERMISSIONS.SUPPLIER_MANAGE}>
            <Button onClick={() => navigate(SUPPLIER_ROUTES.create)}>
              <UserPlus className="h-4 w-4" />
              <span className="hidden sm:inline">Add supplier</span>
            </Button>
          </PermissionGate>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        {/* Search and city have no meaning against the deleted endpoint, which
            returns this tenant's deleted suppliers whole and unpaginated. */}
        <SearchInput value={search} onChange={setSearch} disabled={deletedMode}
                     placeholder="Name, code, mobile, contact or GST…" />

        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {SUPPLIER_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
            {canManage ? <SelectItem value={DELETED}>Deleted</SelectItem> : null}
          </SelectContent>
        </Select>

        <Select value={city} onValueChange={setCity} disabled={deletedMode}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="City" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All cities</SelectItem>
            {cities.map((option) => (
              <SelectItem key={option} value={option}>{option}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {deletedMode ? (
        <Card>
          {deletedError ? (
            <ErrorState error={deletedError} onRetry={reloadDeleted} />
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Supplier</TableHead>
                    <TableHead className="hidden sm:table-cell">Mobile</TableHead>
                    <TableHead className="hidden md:table-cell">City</TableHead>
                    <TableHead>Deleted on</TableHead>
                    <TableHead className="w-12" />
                  </TableRow>
                </TableHeader>

                {deletedLoading ? (
                  <TableSkeleton columns={5} rows={5} />
                ) : (
                  <TableBody>
                    {deletedRows?.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>
                          <span className="font-medium">{row.supplierName}</span>
                          <span className="tabular mt-0.5 block text-xs text-muted-foreground">
                            {row.supplierCode}
                          </span>
                        </TableCell>
                        <TableCell className="tabular hidden sm:table-cell">{row.mobileNo}</TableCell>
                        <TableCell className="hidden md:table-cell">{row.city ?? '—'}</TableCell>
                        <TableCell className="tabular text-sm text-muted-foreground">
                          {formatDateTime(row.deletedAt)}
                        </TableCell>
                        <TableCell>
                          {/* Restoring is not destructive - it undoes a deletion -
                              so it acts directly, like Worker's Reactivate. */}
                          <Button
                            variant="outline"
                            size="sm"
                            loading={restoringId === row.id}
                            onClick={() => handleRestore(row.id, row.supplierName)}
                          >
                            <RotateCcw className="h-4 w-4" />
                            Restore
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>

              {!deletedLoading && deletedRows && deletedRows.length === 0 ? (
                <EmptyState
                  icon={Truck}
                  title="No deleted suppliers"
                  description="Suppliers you deactivate appear here so they can be restored."
                />
              ) : null}
            </>
          )}
        </Card>
      ) : (
      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Supplier</TableHead>
                  <TableHead className="hidden sm:table-cell">Mobile</TableHead>
                  <TableHead className="hidden lg:table-cell">City</TableHead>
                  <TableHead className="hidden xl:table-cell">Payment terms</TableHead>
                  <TableHead className="hidden md:table-cell">Credit limit</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={7} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow
                      key={row.id}
                      className="cursor-pointer"
                      onClick={() => navigate(SUPPLIER_ROUTES.detail(row.id))}
                    >
                      <TableCell>
                        <span className="font-medium">{row.supplierName}</span>
                        <span className="tabular mt-0.5 block text-xs text-muted-foreground">
                          {row.supplierCode}
                        </span>
                      </TableCell>
                      <TableCell className="tabular hidden sm:table-cell">{row.mobileNo}</TableCell>
                      <TableCell className="hidden lg:table-cell">{row.city ?? '—'}</TableCell>
                      <TableCell className="tabular hidden xl:table-cell">
                        {row.paymentTermsDays} days
                      </TableCell>
                      <TableCell className="tabular hidden md:table-cell">
                        ₹{row.creditLimitDisplay}
                      </TableCell>
                      <TableCell><SupplierStatusBadge status={row.status} /></TableCell>
                      <TableCell onClick={(event) => event.stopPropagation()}>
                        <PermissionGate permission={PERMISSIONS.SUPPLIER_MANAGE}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8"
                                      aria-label={`Actions for ${row.supplierName}`}>
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={() => navigate(SUPPLIER_ROUTES.edit(row.id))}>
                                <Pencil className="h-4 w-4" />
                                Edit
                              </DropdownMenuItem>
                              <DropdownMenuItem destructive onClick={() => setDeleting(row)}>
                                <Trash2 className="h-4 w-4" />
                                Deactivate
                              </DropdownMenuItem>
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
                icon={Truck}
                title="No suppliers match these filters"
                description="Try clearing the search box or the status filter."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>
      )}

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Deactivate this supplier?"
        description={`${deleting?.supplierName ?? 'This supplier'} will no longer appear when raising a purchase order. Past purchase history is retained.`}
        confirmLabel="Deactivate"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
