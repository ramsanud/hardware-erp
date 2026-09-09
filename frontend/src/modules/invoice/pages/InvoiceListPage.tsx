import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileText, Plus } from 'lucide-react';
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
import { INVOICE_ROUTES, INVOICE_STATUS_OPTIONS } from '../constants';
import { invoiceService } from '../services/invoiceService';
import { InvoiceStatusBadge } from '../components/InvoiceStatusBadge';
import type { InvoiceStatus, InvoiceSummaryResponse } from '../types';

const ALL = '__all__';
const PERIOD_ALL = '__all__';
const PERIOD_THIS_MONTH = 'this_month';
const PERIOD_LAST_MONTH = 'last_month';

const PERIOD_OPTIONS = [
  { value: PERIOD_ALL, label: 'All time' },
  { value: PERIOD_THIS_MONTH, label: 'This month' },
  { value: PERIOD_LAST_MONTH, label: 'Last month' },
] as const;

/** Local calendar date, not UTC - toISOString() would shift near midnight in IST. */
function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function periodToDates(period: string): { fromDate?: string; toDate?: string } {
  const now = new Date();
  if (period === PERIOD_THIS_MONTH) {
    return {
      fromDate: toIsoDate(new Date(now.getFullYear(), now.getMonth(), 1)),
      toDate: toIsoDate(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
    };
  }
  if (period === PERIOD_LAST_MONTH) {
    return {
      fromDate: toIsoDate(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
      toDate: toIsoDate(new Date(now.getFullYear(), now.getMonth(), 0)),
    };
  }
  return {};
}

/**
 * CR-068. Column catalogue - ids are persisted, so never rename them.
 *
 * Status keeps its full pill rather than the card's colour dot: Unpaid and
 * Partially paid share the warning tone, so a dot alone could not tell them
 * apart (see StatusBadge).
 */
const INVOICE_COLUMNS: ColumnDef<InvoiceSummaryResponse>[] = [
  {
    id: 'invoice',
    header: 'Invoice',
    locked: true,
    cellClassName: 'tabular font-medium',
    cell: (row) => row.invoiceNumber,
  },
  {
    id: 'customer',
    header: 'Customer',
    cell: (row) => (
      <>
        <span>{row.customerName}</span>
        <span className="tabular mt-0.5 block text-xs text-muted-foreground">{row.customerMobile}</span>
      </>
    ),
  },
  {
    id: 'date',
    header: 'Date',
    headClassName: 'hidden sm:table-cell',
    cellClassName: 'hidden sm:table-cell',
    cell: (row) => row.invoiceDate,
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
    cell: (row) => <InvoiceStatusBadge status={row.status} />,
  },
];

export function InvoiceListPage() {
  const navigate = useNavigate();
  const columns = useColumnPreferences('invoice', INVOICE_COLUMNS);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [period, setPeriod] = useState<string>(PERIOD_ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, period, size]);

  const fetcher = useCallback(
    () => invoiceService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as InvoiceStatus),
      ...periodToDates(period),
      page,
      size,
    }),
    [debouncedSearch, status, period, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, period, page, size]);

  return (
    <>
      <PageHeader
        title="Invoices"
        description="Bills raised to customers, with payment status."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <ColumnSettings preferences={columns} label="invoice" />
            <PermissionGate permission={PERMISSIONS.INVOICE_CREATE}>
              <Button onClick={() => navigate(INVOICE_ROUTES.create)}>
                <Plus className="h-4 w-4" />
                <span>New invoice</span>
              </Button>
            </PermissionGate>
          </div>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Invoice number, customer, mobile…" />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {INVOICE_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={period} onValueChange={setPeriod}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Period" /></SelectTrigger>
          <SelectContent>
            {PERIOD_OPTIONS.map((option) => (
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
                      onClick={() => navigate(INVOICE_ROUTES.detail(row.id))}
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
                icon={FileText}
                title="No invoices match these filters"
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
