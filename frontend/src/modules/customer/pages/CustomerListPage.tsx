import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MoreHorizontal, Pencil, UserCheck, Users, UserPlus, UserX } from 'lucide-react';
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
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { UnsavedChangesDialog } from '@/shared/components/UnsavedChangesDialog';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { CUSTOMER_ROUTES, CUSTOMER_STATUS_OPTIONS } from '../constants';
import { customerService } from '../services/customerService';
import { CustomerForm, CUSTOMER_FORM_ID } from '../forms/CustomerForm';
import type { CustomerRequest, CustomerResponse, CustomerStatus, CustomerSummaryResponse } from '../types';

const ALL = '__all__';

export function CustomerListPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [dialogTarget, setDialogTarget] = useState<CustomerResponse | 'new' | null>(null);
  const [dirty, setDirty] = useState(false);
  const [confirmingClose, setConfirmingClose] = useState(false);
  const [deactivating, setDeactivating] = useState<CustomerSummaryResponse | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, size]);

  const fetcher = useCallback(
    () => customerService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as CustomerStatus),
      page,
      size,
    }),
    [debouncedSearch, status, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, page, size]);

  const requestClose = () => {
    if (dirty) { setConfirmingClose(true); return; }
    setDialogTarget(null);
  };

  const handleSubmit = async (request: CustomerRequest) => {
    if (dialogTarget && dialogTarget !== 'new') {
      const updated = await customerService.update(dialogTarget.id, request);
      toast.success(`${updated.customerName} updated.`);
    } else {
      const created = await customerService.create(request);
      toast.success(`${created.customerName} added.`);
    }
    setDirty(false);
    setConfirmingClose(false);
    setDialogTarget(null);
    await reload();
  };

  const handleDeactivate = async () => {
    if (!deactivating) return;
    try {
      await customerService.deactivate(deactivating.id);
      toast.success(`${deactivating.customerName} deactivated.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not deactivate this customer.');
      throw caught;
    }
  };

  /**
   * CR-058. Customer has no soft delete, so reactivating is a plain status
   * change and, like Worker's Reactivate, acts immediately: there is nothing
   * destructive to confirm.
   */
  const handleActivate = async (customer: CustomerSummaryResponse) => {
    try {
      await customerService.activate(customer.id);
      toast.success(`${customer.customerName} reactivated.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not reactivate this customer.');
    }
  };

  return (
    <>
      <PageHeader
        title="Customers"
        description="People and businesses the shop sells to."
        actions={
          <PermissionGate permission={PERMISSIONS.CUSTOMER_MANAGE}>
            <Button onClick={() => setDialogTarget('new')}>
              <UserPlus className="h-4 w-4" />
              <span className="hidden sm:inline">Add customer</span>
            </Button>
          </PermissionGate>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Name, code or mobile…" />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {CUSTOMER_STATUS_OPTIONS.map((option) => (
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
                  <TableHead>Customer</TableHead>
                  <TableHead className="hidden sm:table-cell">Mobile</TableHead>
                  <TableHead className="hidden md:table-cell">City</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={5} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow
                      key={row.id}
                      className="cursor-pointer"
                      onClick={() => navigate(CUSTOMER_ROUTES.detail(row.id))}
                    >
                      <TableCell>
                        <span className="font-medium">{row.customerName}</span>
                        <span className="tabular mt-0.5 block text-xs text-muted-foreground">{row.customerCode}</span>
                      </TableCell>
                      <TableCell className="tabular hidden sm:table-cell">{row.mobileNo}</TableCell>
                      <TableCell className="hidden md:table-cell">{row.city ?? '—'}</TableCell>
                      <TableCell>
                        <span className={row.status === 'ACTIVE' ? 'text-success' : 'text-muted-foreground'}>
                          {row.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                        </span>
                      </TableCell>
                      <TableCell onClick={(event) => event.stopPropagation()}>
                        <PermissionGate permission={PERMISSIONS.CUSTOMER_MANAGE}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8"
                                      aria-label={`Actions for ${row.customerName}`}>
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={async () => setDialogTarget(await customerService.get(row.id))}>
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
                icon={Users}
                title="No customers match these filters"
                description="Try clearing the search box or the status filter."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={dialogTarget !== null} onOpenChange={(open) => { if (!open) requestClose(); }}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{dialogTarget === 'new' ? 'Add customer' : 'Edit customer'}</DialogTitle>
          </DialogHeader>
          {dialogTarget ? (
            <CustomerForm
              customer={dialogTarget === 'new' ? undefined : dialogTarget}
              onSubmit={handleSubmit}
              onCancel={requestClose}
              onDirtyChange={setDirty}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <UnsavedChangesDialog
        open={confirmingClose}
        onContinueEditing={() => setConfirmingClose(false)}
        onDiscard={() => { setConfirmingClose(false); setDirty(false); setDialogTarget(null); }}
        formId={CUSTOMER_FORM_ID}
      />

      <ConfirmDialog
        open={deactivating !== null}
        onOpenChange={(open) => !open && setDeactivating(null)}
        title="Deactivate this customer?"
        description={`${deactivating?.customerName ?? 'This customer'} will no longer appear when raising an invoice or quotation. Past history is retained.`}
        confirmLabel="Deactivate"
        destructive
        onConfirm={handleDeactivate}
      />
    </>
  );
}
