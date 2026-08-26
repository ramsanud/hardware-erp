import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Wallet } from 'lucide-react';
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
import { formatDateTime } from '@/shared/lib/utils';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { PAYMENT_METHOD_OPTIONS } from '../constants';
import { paymentService } from '../services/paymentService';
import type { PaymentMethod } from '../types';

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

const METHOD_LABELS: Record<string, string> = Object.fromEntries(
  PAYMENT_METHOD_OPTIONS.map((option) => [option.value, option.label]),
);

export function PaymentListPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [method, setMethod] = useState<string>(ALL);
  const [period, setPeriod] = useState<string>(PERIOD_ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, method, period, size]);

  const fetcher = useCallback(
    () => paymentService.search({
      search: debouncedSearch || undefined,
      paymentMethod: method === ALL ? undefined : (method as PaymentMethod),
      ...periodToDates(period),
      page,
      size,
    }),
    [debouncedSearch, method, period, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, method, period, page, size]);

  return (
    <>
      <PageHeader
        title="Payments"
        description="Every payment recorded against every invoice, in one place."
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Invoice number, customer, mobile…" />
        <Select value={method} onValueChange={setMethod}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Method" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All methods</SelectItem>
            {PAYMENT_METHOD_OPTIONS.map((option) => (
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
                  <TableHead>Invoice</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead className="hidden sm:table-cell">Method</TableHead>
                  <TableHead className="hidden sm:table-cell">Date</TableHead>
                  <TableHead className="hidden md:table-cell">Notes</TableHead>
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={6} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="tabular font-medium">
                        <button
                          type="button"
                          className="hover:underline"
                          onClick={() => navigate(INVOICE_ROUTES.detail(row.invoiceId))}
                        >
                          {row.invoiceNumber}
                        </button>
                      </TableCell>
                      <TableCell>
                        <span>{row.customerName}</span>
                        <span className="tabular mt-0.5 block text-xs text-muted-foreground">{row.customerMobile}</span>
                      </TableCell>
                      <TableCell className="tabular">₹{row.amountDisplay}</TableCell>
                      <TableCell className="hidden sm:table-cell">
                        {METHOD_LABELS[row.paymentMethod] ?? row.paymentMethod}
                      </TableCell>
                      <TableCell className="hidden sm:table-cell">{formatDateTime(row.paymentDate)}</TableCell>
                      <TableCell className="hidden max-w-xs truncate md:table-cell text-muted-foreground">
                        {row.notes || '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={Wallet}
                title="No payments match these filters"
                description="Try clearing the search box or the method filter."
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
