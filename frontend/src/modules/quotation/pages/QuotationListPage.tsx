import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Download, Plus } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import { DatePicker } from '@/shared/components/ui/date-picker';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/shared/components/ui/tabs';
import {
  ColumnSettings, useColumnPreferences, type ColumnDef,
} from '@/shared/components/table/ColumnPreferences';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { useAsyncData } from '@/shared/hooks/useAsyncData';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { downloadBlob, formatDate } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { QUOTATION_ROUTES } from '../constants';
import { quotationService } from '../services/quotationService';
import { QuotationStatusBadge } from '../components/QuotationStatusBadge';
import { QuotationKpiCards } from '../components/QuotationKpiCards';
import { QuotationEmptyState } from '../components/QuotationEmptyState';
import { QuotationRowActions } from '../components/QuotationRowActions';
import type { QuotationStatus, QuotationSummaryResponse } from '../types';

const ALL = 'ALL';

/**
 * The pills. Rejected has no pill of its own - the brief's six are the ones a
 * counter scans for, and a rejected quote is reachable through "All" and the
 * Expired / Rejected card. The order is the life of a quotation.
 */
const STATUS_PILLS: { value: typeof ALL | QuotationStatus; label: string }[] = [
  { value: ALL, label: 'All' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'SENT', label: 'Sent' },
  { value: 'ACCEPTED', label: 'Accepted' },
  { value: 'CONVERTED', label: 'Converted' },
  { value: 'EXPIRED', label: 'Expired' },
];

type DatePreset = 'all' | 'today' | 'month' | 'custom';

const DATE_PRESETS: { value: DatePreset; label: string }[] = [
  { value: 'all', label: 'All time' },
  { value: 'today', label: 'Today' },
  { value: 'month', label: 'This month' },
  { value: 'custom', label: 'Custom range' },
];

/** Local calendar date as yyyy-MM-dd - toISOString() would hand back yesterday after 5:30 pm IST. */
function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function today(): string {
  return isoDate(new Date());
}

function firstOfMonth(): string {
  const now = new Date();
  return isoDate(new Date(now.getFullYear(), now.getMonth(), 1));
}

/** Whole days from today to validUntil; negative once it has passed. */
function daysUntil(validUntil: string): number {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(validUntil);
  if (!match) return Number.NaN;
  const target = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target.getTime() - start.getTime()) / 86_400_000);
}

const EXPIRING_SOON_DAYS = 3;

/** The amber warning beside Valid until for a live quotation about to lapse. */
function ExpiringBadge({ row }: { row: QuotationSummaryResponse }) {
  const live = row.status === 'DRAFT' || row.status === 'SENT' || row.status === 'ACCEPTED';
  if (!live || row.expired) return null;
  const days = daysUntil(row.validUntil);
  if (Number.isNaN(days) || days > EXPIRING_SOON_DAYS) return null;
  const label = days <= 0 ? 'Expires today' : days === 1 ? '1 day left' : `${days} days left`;
  return <Badge variant="warning" className="ml-2 align-middle" data-testid="expiring-soon">{label}</Badge>;
}

/** CR-068. Column catalogue - ids are persisted, so never rename them. */
const QUOTATION_COLUMNS: ColumnDef<QuotationSummaryResponse>[] = [
  {
    id: 'quotation',
    header: 'Quotation',
    locked: true,
    cell: (row) => (
      <>
        <span className="tabular font-medium">{row.quotationNumber}</span>
        <span className="tabular mt-0.5 block text-xs text-muted-foreground">{formatDate(row.quotationDate)}</span>
      </>
    ),
  },
  {
    id: 'customer',
    header: 'Customer',
    cell: (row) => (
      <>
        <span>{row.customerName}</span>
        <span className="tabular mt-0.5 block text-xs text-muted-foreground">
          {row.customerMobile ? `+91 ${row.customerMobile}` : '—'}
        </span>
      </>
    ),
  },
  {
    id: 'validUntil',
    header: 'Valid until',
    headClassName: 'hidden sm:table-cell',
    cellClassName: 'hidden sm:table-cell whitespace-nowrap',
    cell: (row) => (
      <>
        <span className="tabular">{formatDate(row.validUntil)}</span>
        <ExpiringBadge row={row} />
      </>
    ),
  },
  {
    id: 'total',
    header: 'Amount',
    cellClassName: 'tabular font-medium',
    cell: (row) => `₹${row.totalDisplay}`,
  },
  {
    id: 'status',
    header: 'Status',
    cellClassName: 'status-on-card',
    cell: (row) => <QuotationStatusBadge status={row.status} expired={row.expired} />,
  },
];

const CSV_PAGE = 100;
const CSV_MAX_ROWS = 5000;

function csvCell(value: string | number): string {
  const text = String(value ?? '');
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function QuotationListPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const columns = useColumnPreferences('quotation', QUOTATION_COLUMNS);

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<typeof ALL | QuotationStatus>(ALL);
  const [preset, setPreset] = useState<DatePreset>('all');
  const [customFrom, setCustomFrom] = useState(firstOfMonth());
  const [customTo, setCustomTo] = useState(today());
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [converting, setConverting] = useState<QuotationSummaryResponse | null>(null);
  const [deleting, setDeleting] = useState<QuotationSummaryResponse | null>(null);
  const [exporting, setExporting] = useState(false);
  const [openingEdit, setOpeningEdit] = useState<number | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  // The date window the preset resolves to. Custom is the two pickers.
  const { fromDate, toDate } = useMemo(() => {
    switch (preset) {
      case 'today': return { fromDate: today(), toDate: today() };
      case 'month': return { fromDate: firstOfMonth(), toDate: today() };
      case 'custom': return { fromDate: customFrom || undefined, toDate: customTo || undefined };
      default: return { fromDate: undefined, toDate: undefined };
    }
  }, [preset, customFrom, customTo]);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, fromDate, toDate, size]);

  const fetcher = useCallback(
    () => quotationService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : status,
      fromDate,
      toDate,
      page,
      size,
    }),
    [debouncedSearch, status, fromDate, toDate, page, size],
  );
  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, fromDate, toDate, page, size]);

  // The cards follow the search and the date range but never the status
  // pill: a card that only counted the pill you are on would say nothing.
  const statsFetcher = useCallback(
    () => quotationService.stats({ search: debouncedSearch || undefined, fromDate, toDate }),
    [debouncedSearch, fromDate, toDate],
  );
  const stats = useAsyncData(statsFetcher, [debouncedSearch, fromDate, toDate]);

  const filtered = Boolean(debouncedSearch) || status !== ALL || preset !== 'all';

  const clearFilters = () => {
    setSearch('');
    setStatus(ALL);
    setPreset('all');
  };

  const refreshAll = async () => {
    await Promise.all([reload(), stats.reload()]);
  };

  const handleConvert = async () => {
    if (!converting) return;
    try {
      const updated = await quotationService.convert(converting.id);
      toast.success(`${converting.quotationNumber} converted to an invoice.`);
      if (updated.convertedInvoiceId) {
        navigate(INVOICE_ROUTES.detail(updated.convertedInvoiceId));
      } else {
        await refreshAll();
      }
    } catch (caught) {
      toast.error(caught, 'Could not convert this quotation to an invoice.');
      throw caught;
    }
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await quotationService.delete(deleting.id);
      toast.success(`${deleting.quotationNumber} deleted.`);
      await refreshAll();
    } catch (caught) {
      toast.error(caught, 'Could not delete this quotation.');
      throw caught;
    }
  };

  /** Same hand-off the detail page's Edit uses: the wizard needs the lines, and the summary row has none. */
  const handleEdit = async (row: QuotationSummaryResponse) => {
    setOpeningEdit(row.id);
    try {
      const quotation = await quotationService.get(row.id);
      navigate(QUOTATION_ROUTES.create, {
        state: {
          editQuotationId: quotation.id,
          editQuotationNumber: quotation.quotationNumber,
          customer: { customerName: quotation.customerName, customerMobile: quotation.customerMobile },
          items: quotation.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
        },
      });
    } catch (caught) {
      toast.error(caught, 'Could not open this quotation for editing.');
    } finally {
      setOpeningEdit(null);
    }
  };

  /**
   * Client-side, over the current filters, every page. The display strings
   * are what the shop sees on screen, so they are what lands in the file.
   * Capped so a mis-click on "All time" in a busy shop cannot pull the whole
   * table through the browser; the toast says when the cap was hit.
   */
  const handleExport = async () => {
    setExporting(true);
    try {
      const rows: QuotationSummaryResponse[] = [];
      let next = 0;
      let last = false;
      while (!last && rows.length < CSV_MAX_ROWS) {
        const chunk = await quotationService.search({
          search: debouncedSearch || undefined,
          status: status === ALL ? undefined : status,
          fromDate,
          toDate,
          page: next,
          size: CSV_PAGE,
        });
        rows.push(...chunk.content);
        last = chunk.last || chunk.content.length === 0;
        next += 1;
      }
      const capped = rows.length >= CSV_MAX_ROWS;
      const lines = [
        ['Quotation #', 'Date', 'Customer', 'Mobile', 'Valid until', 'Amount (INR)', 'Status'].join(','),
        ...rows.slice(0, CSV_MAX_ROWS).map((row) => [
          row.quotationNumber,
          row.quotationDate,
          row.customerName,
          row.customerMobile,
          row.validUntil,
          row.totalDisplay.replace(/,/g, ''),
          row.expired && (row.status === 'DRAFT' || row.status === 'SENT' || row.status === 'ACCEPTED') ? 'EXPIRED' : row.status,
        ].map(csvCell).join(',')),
      ];
      // The BOM is for Excel, which otherwise reads ₹-free but UTF-8 names as Latin-1.
      const blob = new Blob([`﻿${lines.join('\r\n')}`], { type: 'text/csv;charset=utf-8' });
      const suffix = fromDate || toDate ? `-${fromDate ?? 'start'}-to-${toDate ?? 'today'}` : '';
      downloadBlob(blob, `quotations${suffix}.csv`);
      toast.success(capped
        ? `Exported the first ${CSV_MAX_ROWS} quotations - narrow the date range for the rest.`
        : `Exported ${rows.length} quotation${rows.length === 1 ? '' : 's'}.`);
    } catch (caught) {
      toast.error(caught, 'Could not export the quotations.');
    } finally {
      setExporting(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Quotations"
        description="Price quotes for customers - independent of whether they buy."
      />

      <QuotationKpiCards stats={stats.data} loading={stats.loading} />

      {/* Toolbar - search and date window on the first row, pills and the buttons on the second. */}
      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <SearchInput value={search} onChange={setSearch} placeholder="Quotation #, customer name, mobile…" />
          <Select value={preset} onValueChange={(value) => setPreset(value as DatePreset)}>
            <SelectTrigger className="sm:w-40" aria-label="Date range"><SelectValue /></SelectTrigger>
            <SelectContent>
              {DATE_PRESETS.map((option) => (
                <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          {preset === 'custom' ? (
            <div className="flex items-center gap-2">
              <DatePicker id="quotationFrom" value={customFrom} max={customTo || undefined}
                          onChange={setCustomFrom} placeholder="From" className="sm:w-36" />
              <span className="text-sm text-muted-foreground">to</span>
              <DatePicker id="quotationTo" value={customTo} min={customFrom || undefined}
                          onChange={setCustomTo} placeholder="To" className="sm:w-36" />
            </div>
          ) : null}
        </div>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <Tabs value={status} onValueChange={(value) => setStatus(value as typeof ALL | QuotationStatus)}>
            <TabsList aria-label="Filter by status" className="sm:justify-start">
              {STATUS_PILLS.map((pill) => (
                <TabsTrigger key={pill.value} value={pill.value}>{pill.label}</TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
          <div className="flex flex-wrap items-center gap-2">
            <ColumnSettings preferences={columns} label="quotation" />
            <Button variant="outline" onClick={() => void handleExport()} loading={exporting}
                    disabled={!data || data.totalElements === 0}>
              <Download className="h-4 w-4" />
              <span>Export CSV</span>
            </Button>
            <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
              <Button onClick={() => navigate(QUOTATION_ROUTES.create)}>
                <Plus className="h-4 w-4" />
                <span>New quotation</span>
              </Button>
            </PermissionGate>
          </div>
        </div>
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
                  <TableHead className="w-12"><span className="sr-only">Actions</span></TableHead>
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={columns.visible.length + 1} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow
                      key={row.id}
                      className="cursor-pointer"
                      onClick={() => navigate(QUOTATION_ROUTES.detail(row.id))}
                      aria-busy={openingEdit === row.id || undefined}
                    >
                      {columns.visible.map((column) => (
                        <TableCell key={column.id} className={columns.resolveClassName(column, 'cell')}>
                          {column.cell(row)}
                        </TableCell>
                      ))}
                      <TableCell onClick={(event) => event.stopPropagation()} label="Actions">
                        <QuotationRowActions
                          row={row}
                          onConvert={setConverting}
                          onEdit={(target) => void handleEdit(target)}
                          onDelete={setDeleting}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <QuotationEmptyState
                filtered={filtered}
                onCreate={() => navigate(QUOTATION_ROUTES.create)}
                onClearFilters={clearFilters}
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <ConfirmDialog
        open={converting !== null}
        onOpenChange={(open) => !open && setConverting(null)}
        title={`Convert ${converting?.quotationNumber ?? 'this quotation'} to an invoice?`}
        description="Stock will be decremented and a real invoice will be created, priced at each product's current rate (not the price shown on this quote)."
        confirmLabel="Convert"
        onConfirm={handleConvert}
      />

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={`Delete draft ${deleting?.quotationNumber ?? ''}?`}
        description="The draft and its lines are removed for good. Nothing was issued to the customer, so there is nothing else to undo."
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
