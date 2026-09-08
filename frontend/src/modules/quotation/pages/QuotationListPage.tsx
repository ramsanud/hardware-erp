import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileSpreadsheet, Plus } from 'lucide-react';
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
import { QUOTATION_ROUTES, QUOTATION_STATUS_OPTIONS } from '../constants';
import { quotationService } from '../services/quotationService';
import { QuotationStatusBadge } from '../components/QuotationStatusBadge';
import type { QuotationStatus, QuotationSummaryResponse } from '../types';

const ALL = '__all__';

/** CR-068. Column catalogue - ids are persisted, so never rename them. */
const QUOTATION_COLUMNS: ColumnDef<QuotationSummaryResponse>[] = [
  {
    id: 'quotation',
    header: 'Quotation',
    locked: true,
    cellClassName: 'tabular font-medium',
    cell: (row) => row.quotationNumber,
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
    id: 'validUntil',
    header: 'Valid until',
    headClassName: 'hidden sm:table-cell',
    cellClassName: 'hidden sm:table-cell',
    cell: (row) => row.validUntil,
  },
  {
    id: 'total',
    header: 'Total',
    cellClassName: 'tabular',
    cell: (row) => `₹${row.totalDisplay}`,
  },
  {
    id: 'status',
    header: 'Status',
    cell: (row) => <QuotationStatusBadge status={row.status} expired={row.expired} />,
  },
  {
    id: 'quotationDate',
    header: 'Raised on',
    defaultVisible: false,
    cell: (row) => row.quotationDate,
  },
];

export function QuotationListPage() {
  const navigate = useNavigate();
  const columns = useColumnPreferences('quotation', QUOTATION_COLUMNS);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, size]);

  const fetcher = useCallback(
    () => quotationService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as QuotationStatus),
      page,
      size,
    }),
    [debouncedSearch, status, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, page, size]);

  return (
    <>
      <PageHeader
        title="Quotations"
        description="Price quotes for customers - independent of whether they buy."
        actions={
          <div className="flex items-center gap-2">
            <ColumnSettings preferences={columns} label="quotation" />
            <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
              <Button onClick={() => navigate(QUOTATION_ROUTES.create)}>
                <Plus className="h-4 w-4" />
                <span>New quotation</span>
              </Button>
            </PermissionGate>
          </div>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Quotation number, customer, mobile…" />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {QUOTATION_STATUS_OPTIONS.map((option) => (
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
                      onClick={() => navigate(QUOTATION_ROUTES.detail(row.id))}
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
                icon={FileSpreadsheet}
                title="No quotations match these filters"
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
