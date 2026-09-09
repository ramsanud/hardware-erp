import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, ShoppingCart, Upload } from 'lucide-react';
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
import { PURCHASE_ROUTES, PURCHASE_STATUS_OPTIONS } from '../constants';
import { purchaseService } from '../services/purchaseService';
import { PurchaseStatusBadge } from '../components/PurchaseStatusBadge';
import { ImportSupplierBillDialog } from '../components/ImportSupplierBillDialog';
import type { PurchaseStatus, PurchaseSummaryResponse } from '../types';

const ALL = '__all__';

/** CR-068. Column catalogue - ids are persisted, so never rename them. */
const PURCHASE_COLUMNS: ColumnDef<PurchaseSummaryResponse>[] = [
  {
    id: 'purchase',
    header: 'Purchase',
    locked: true,
    cellClassName: 'tabular font-medium',
    cell: (row) => (
      <>
        {row.purchaseNumber}
        {row.imported ? <span className="ml-2 text-xs text-muted-foreground">(imported)</span> : null}
      </>
    ),
  },
  {
    id: 'supplier',
    header: 'Supplier',
    cell: (row) => (
      <>
        <span>{row.supplierName}</span>
        {row.supplierBillNumber ? (
          <span className="mt-0.5 block text-xs text-muted-foreground">Bill #{row.supplierBillNumber}</span>
        ) : null}
      </>
    ),
  },
  {
    id: 'date',
    header: 'Date',
    headClassName: 'hidden sm:table-cell',
    cellClassName: 'hidden sm:table-cell',
    cell: (row) => row.purchaseDate,
  },
  {
    id: 'total',
    header: 'Total',
    cellClassName: 'tabular',
    cell: (row) => `₹${row.totalDisplay}`,
  },
  {
    id: 'balance',
    header: 'Balance',
    headClassName: 'hidden md:table-cell',
    cellClassName: 'tabular hidden md:table-cell',
    cell: (row) => `₹${row.balanceDisplay}`,
  },
  {
    id: 'status',
    header: 'Status',
    cell: (row) => <PurchaseStatusBadge status={row.status} />,
  },
  {
    id: 'supplierBillNumber',
    header: 'Supplier bill no.',
    defaultVisible: false,
    cellClassName: 'tabular',
    cell: (row) => row.supplierBillNumber || '—',
  },
];

export function PurchaseListPage() {
  const navigate = useNavigate();
  const columns = useColumnPreferences('purchase', PURCHASE_COLUMNS);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [importOpen, setImportOpen] = useState(false);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, size]);

  const fetcher = useCallback(
    () => purchaseService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as PurchaseStatus),
      page,
      size,
    }),
    [debouncedSearch, status, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, page, size]);

  return (
    <>
      <PageHeader
        title="Purchases"
        description="Bills received from suppliers, with payment status."
        actions={
          <div className="flex items-center gap-2">
            <ColumnSettings preferences={columns} label="purchase" />
            <PermissionGate permission={PERMISSIONS.PURCHASE_MANAGE}>
              <Button variant="outline" onClick={() => setImportOpen(true)}>
                <Upload className="h-4 w-4" />
                <span>Import supplier bill</span>
              </Button>
              <Button onClick={() => navigate(PURCHASE_ROUTES.create)}>
                <Plus className="h-4 w-4" />
                <span>New purchase</span>
              </Button>
            </PermissionGate>
          </div>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Purchase number, supplier, bill number…" />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {PURCHASE_STATUS_OPTIONS.map((option) => (
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
                    <TableRow
                      key={row.id}
                      className="cursor-pointer"
                      onClick={() => navigate(PURCHASE_ROUTES.detail(row.id))}
                    >
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
                icon={ShoppingCart}
                title="No purchases match these filters"
                description="Try clearing the search box or the status filter, or record your first purchase."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <ImportSupplierBillDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        onImported={(purchaseId) => { setImportOpen(false); navigate(PURCHASE_ROUTES.detail(purchaseId)); }}
      />
    </>
  );
}
