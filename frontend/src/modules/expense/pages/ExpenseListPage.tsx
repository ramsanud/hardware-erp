import { useCallback, useEffect, useState } from 'react';
import {
  Coins, MoreHorizontal, Pencil, Plus, Receipt, X,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
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
import { EXPENSE_STATUS_OPTIONS } from '../constants';
import { expenseService } from '../services/expenseService';
import { expenseCategoryService } from '../services/expenseCategoryService';
import { ExpenseForm } from '../forms/ExpenseForm';
import { ExpenseStatusBadge } from '../components/ExpenseStatusBadge';
import type {
  BusinessExpenseResponse, ExpenseCategoryResponse, ExpenseStatus, ExpenseTotalResponse,
} from '../types';
import type { ExpenseValues } from '../validation/schemas';

const ALL = '__all__';

export function ExpenseListPage() {
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [categoryId, setCategoryId] = useState<string>(ALL);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [categories, setCategories] = useState<ExpenseCategoryResponse[]>([]);
  const [total, setTotal] = useState<ExpenseTotalResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<BusinessExpenseResponse | null>(null);
  const [cancelling, setCancelling] = useState<BusinessExpenseResponse | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, categoryId, fromDate, toDate, size]);

  const fetcher = useCallback(
    () => expenseService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as ExpenseStatus),
      categoryId: categoryId === ALL ? undefined : Number(categoryId),
      fromDate: fromDate || undefined,
      toDate: toDate || undefined,
      page,
      size,
    }),
    [debouncedSearch, status, categoryId, fromDate, toDate, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher,
    [debouncedSearch, status, categoryId, fromDate, toDate, page, size]);

  const reloadTotal = useCallback(() => {
    expenseService.total(fromDate || undefined, toDate || undefined)
      .then(setTotal)
      .catch(() => setTotal(null));
  }, [fromDate, toDate]);

  useEffect(() => { reloadTotal(); }, [reloadTotal]);

  useEffect(() => {
    expenseCategoryService.list().then(setCategories).catch(() => setCategories([]));
  }, []);

  const handleCategoryCreated = (category: ExpenseCategoryResponse) => {
    setCategories((current) => [...current, category].sort((a, b) => a.name.localeCompare(b.name)));
  };

  const toRequest = (values: ExpenseValues) => ({
    expenseDate: values.expenseDate,
    categoryId: values.categoryId,
    amountPaise: Math.round(Number(values.amountRupees) * 100),
    paymentMethod: values.paymentMethod,
    notes: values.notes || null,
  });

  const handleCreate = async (values: ExpenseValues) => {
    await expenseService.create(toRequest(values));
    setCreating(false);
    toast.success('Expense recorded.');
    await reload();
    reloadTotal();
  };

  const handleUpdate = async (values: ExpenseValues) => {
    if (!editing) return;
    await expenseService.update(editing.id, toRequest(values));
    setEditing(null);
    toast.success('Expense updated.');
    await reload();
    reloadTotal();
  };

  const handleCancel = async () => {
    if (!cancelling) return;
    try {
      await expenseService.cancel(cancelling.id);
      toast.success('Expense cancelled.');
      await reload();
      reloadTotal();
    } catch (caught) {
      toast.error(caught, 'Could not cancel this expense.');
      throw caught;
    }
  };

  return (
    <>
      <PageHeader
        title="Expenses"
        description="Shop-wide costs - rent, salaries, utilities and everything else, separate from project costs."
        actions={
          <PermissionGate permission={PERMISSIONS.EXPENSE_MANAGE}>
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" />
              <span className="hidden sm:inline">Add expense</span>
            </Button>
          </PermissionGate>
        }
      />

      <Card>
        <CardContent className="flex items-center justify-between py-4">
          <div className="flex items-center gap-2">
            <Coins className="h-5 w-5 text-primary" aria-hidden />
            <span className="text-sm text-muted-foreground">
              Total{fromDate || toDate ? ' (filtered range)' : ''}
            </span>
          </div>
          <span className="tabular text-xl font-semibold">₹{total?.totalAmountDisplay ?? '0.00'}</span>
        </CardContent>
      </Card>

      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Search notes, category…" />

        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {EXPENSE_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={categoryId} onValueChange={setCategoryId}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Category" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All categories</SelectItem>
            {categories.map((option) => (
              <SelectItem key={option.id} value={String(option.id)}>{option.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}
               className="sm:w-40" aria-label="From date" />
        <Input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}
               className="sm:w-40" aria-label="To date" />
        {fromDate || toDate ? (
          <Button variant="ghost" size="sm" onClick={() => { setFromDate(''); setToDate(''); }}>
            <X className="h-3.5 w-3.5" />
            Clear dates
          </Button>
        ) : null}
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead className="hidden sm:table-cell">Notes</TableHead>
                  <TableHead className="hidden md:table-cell">Method</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={7} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="tabular">{row.expenseDate}</TableCell>
                      <TableCell>
                        <span className="font-medium">{row.categoryName}</span>
                        {row.hasReceipt ? <Receipt className="ml-1.5 inline h-3.5 w-3.5 text-muted-foreground" aria-label="Has receipt" /> : null}
                      </TableCell>
                      <TableCell className="hidden max-w-xs truncate sm:table-cell">{row.notes ?? '—'}</TableCell>
                      <TableCell className="hidden md:table-cell">{row.paymentMethod}</TableCell>
                      <TableCell className="tabular text-right">₹{row.amountDisplay}</TableCell>
                      <TableCell><ExpenseStatusBadge status={row.status} /></TableCell>
                      <TableCell>
                        <PermissionGate permission={PERMISSIONS.EXPENSE_MANAGE}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8"
                                      aria-label={`Actions for this expense`}>
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={() => setEditing(row)}>
                                <Pencil className="h-4 w-4" />
                                Edit
                              </DropdownMenuItem>
                              {row.status === 'ACTIVE' ? (
                                <DropdownMenuItem destructive onClick={() => setCancelling(row)}>
                                  <X className="h-4 w-4" />
                                  Cancel
                                </DropdownMenuItem>
                              ) : null}
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
                icon={Coins}
                title="No expenses match these filters"
                description="Try clearing the search box, status, category or date filters."
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
          <DialogHeader><DialogTitle>Add expense</DialogTitle></DialogHeader>
          <ExpenseForm categories={categories} onSubmit={handleCreate} onCancel={() => setCreating(false)}
                       onCategoryCreated={handleCategoryCreated} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader><DialogTitle>Edit expense</DialogTitle></DialogHeader>
          {editing ? (
            <ExpenseForm expense={editing} categories={categories} onSubmit={handleUpdate}
                         onCancel={() => setEditing(null)} onCategoryCreated={handleCategoryCreated}
                         onReceiptChanged={reload} />
          ) : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={cancelling !== null}
        onOpenChange={(open) => !open && setCancelling(null)}
        title="Cancel this expense?"
        description="It stays in the ledger for record-keeping, but is excluded from the running total."
        confirmLabel="Cancel expense"
        destructive
        onConfirm={handleCancel}
      />
    </>
  );
}
