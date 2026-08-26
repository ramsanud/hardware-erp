import { useCallback, useEffect, useState } from 'react';
import { MoreHorizontal, Pencil, Plus, Ticket, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import { Badge } from '@/shared/components/ui/badge';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
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
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { COUPON_STATUS_OPTIONS } from '../constants';
import { couponService } from '../services/couponService';
import { CouponForm } from '../forms/CouponForm';
import { CouponStatusBadge } from '../components/CouponStatusBadge';
import { toCouponRequest } from '../lib/toCouponRequest';
import type { CouponResponse, CouponStatus, CouponSummaryResponse } from '../types';
import type { CouponValues } from '../validation/schemas';

const ALL = '__all__';

function formatDiscount(row: CouponSummaryResponse): string {
  return row.discountType === 'PERCENT' ? `${row.discountValue}% off` : `₹${row.discountValue} off`;
}

function formatUsage(row: CouponSummaryResponse): string {
  return row.usageLimit ? `${row.timesUsed} / ${row.usageLimit}` : `${row.timesUsed} / Unlimited`;
}

export function CouponListPage() {
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<CouponSummaryResponse | null>(null);
  const [editingFull, setEditingFull] = useState<CouponResponse | null>(null);
  const [deleting, setDeleting] = useState<CouponSummaryResponse | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, size]);

  const fetcher = useCallback(
    () => couponService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as CouponStatus),
      page,
      size,
    }),
    [debouncedSearch, status, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, page, size]);

  useEffect(() => {
    if (!editing) { setEditingFull(null); return; }
    void (async () => {
      try {
        setEditingFull(await couponService.get(editing.id));
      } catch (caught) {
        toast.error(caught, 'Could not load this coupon.');
        setEditing(null);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing]);

  const handleCreate = async (values: CouponValues) => {
    await couponService.create(toCouponRequest(values));
    setCreating(false);
    toast.success('Coupon created.');
    await reload();
  };

  const handleUpdate = async (values: CouponValues) => {
    if (!editingFull) return;
    await couponService.update(editingFull.id, toCouponRequest(values));
    setEditing(null);
    toast.success('Coupon updated.');
    await reload();
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await couponService.remove(deleting.id);
      toast.success(`${deleting.code} has been removed.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not remove this coupon.');
      throw caught;
    }
  };

  return (
    <>
      <PageHeader
        title="Coupons"
        description="Discount codes customers can redeem on an invoice."
        actions={
          <PermissionGate permission={PERMISSIONS.COUPON_MANAGE}>
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" />
              <span className="hidden sm:inline">Add coupon</span>
            </Button>
          </PermissionGate>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Search by code…" />

        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {COUPON_STATUS_OPTIONS.map((option) => (
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
                  <TableHead>Code</TableHead>
                  <TableHead className="hidden sm:table-cell">Discount</TableHead>
                  <TableHead className="hidden md:table-cell">Usage</TableHead>
                  <TableHead className="hidden lg:table-cell">Currently valid</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={6} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>
                        <span className="tabular font-medium">{row.code}</span>
                        {row.restrictedToProducts ? (
                          <Badge variant="outline" className="ml-2">Restricted</Badge>
                        ) : null}
                      </TableCell>
                      <TableCell className="tabular hidden sm:table-cell">{formatDiscount(row)}</TableCell>
                      <TableCell className="tabular hidden md:table-cell">{formatUsage(row)}</TableCell>
                      <TableCell className="hidden lg:table-cell">
                        <Badge variant={row.currentlyValid ? 'success' : 'secondary'}>
                          {row.currentlyValid ? 'Yes' : 'No'}
                        </Badge>
                      </TableCell>
                      <TableCell><CouponStatusBadge status={row.status} /></TableCell>
                      <TableCell>
                        <PermissionGate permission={PERMISSIONS.COUPON_MANAGE}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8"
                                      aria-label={`Actions for ${row.code}`}>
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={() => setEditing(row)}>
                                <Pencil className="h-4 w-4" />
                                Edit
                              </DropdownMenuItem>
                              <DropdownMenuItem destructive onClick={() => setDeleting(row)}>
                                <Trash2 className="h-4 w-4" />
                                Delete
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
                icon={Ticket}
                title="No coupons match these filters"
                description="Try clearing the search box or the status filter."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={creating} onOpenChange={(open) => !open && setCreating(false)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader><DialogTitle>Add coupon</DialogTitle></DialogHeader>
          <CouponForm onSubmit={handleCreate} onCancel={() => setCreating(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader><DialogTitle>Edit coupon</DialogTitle></DialogHeader>
          {editingFull ? (
            <CouponForm coupon={editingFull} onSubmit={handleUpdate} onCancel={() => setEditing(null)} />
          ) : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this coupon?"
        description={`"${deleting?.code ?? 'This coupon'}" will be permanently removed.`}
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
