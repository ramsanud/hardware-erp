import { useState } from 'react';
import { Badge } from '@/shared/components/ui/badge';
import { DatePicker } from '@/shared/components/ui/date-picker';
import { FormField } from '@/shared/components/FormField';
import { useAsyncData } from '@/shared/hooks/useAsyncData';
import { formatDate } from '@/shared/lib/utils';
import { reportService } from '../services/reportService';
import type {
  AgeingRow, DayBookEntry, DayBookKind, GstRateRow, GstSection, PurchaseRegisterRow, StockValuationRow,
} from '../types';
import {
  DateRangeBar, DownloadButtons, ReportNote, ReportTable, defaultRange, today,
} from './ReportShell';
import type { DateRange, ReportColumn } from './ReportShell';

/*
  CR-086. One panel per report. Each owns its filters, fetches on change,
  and hands the same filters to the download buttons so the file is always
  the table on screen.
*/

const KIND_LABEL: Record<DayBookKind, string> = {
  SALE: 'Sale',
  RECEIPT: 'Receipt',
  CREDIT_NOTE: 'Credit note',
  PURCHASE: 'Purchase',
  EXPENSE: 'Expense',
};

/* Tone by direction: money in is success, money out is warning, a reversal is muted. */
const KIND_CLASS: Record<DayBookKind, string> = {
  SALE: 'bg-primary/10 text-primary border-primary/20',
  RECEIPT: 'bg-success/10 text-success border-success/20',
  CREDIT_NOTE: 'bg-muted text-muted-foreground border-border',
  PURCHASE: 'bg-warning/10 text-warning border-warning/20',
  EXPENSE: 'bg-destructive/10 text-destructive border-destructive/20',
};

const TONE_CLASS = { success: 'text-success', warning: 'text-warning', destructive: 'text-destructive' } as const;

function Stat({ label, value, tone }: { label: string; value: string; tone?: keyof typeof TONE_CLASS }) {
  return (
    <div className="min-w-[8rem] rounded-lg border bg-card px-3 py-2" data-report-stat={label}>
      <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className={`mt-0.5 text-base font-semibold tabular-nums ${tone ? TONE_CLASS[tone] : ''}`}>₹{value}</p>
    </div>
  );
}

// ----------------------------------------------------------------- Day Book

export function DayBookPanel() {
  const [range, setRange] = useState<DateRange>(defaultRange);
  const { data, loading, error, reload } = useAsyncData(
    () => reportService.dayBook(range.from, range.to), [range.from, range.to],
  );

  const columns: ReportColumn<DayBookEntry>[] = [
    { id: 'date', header: 'Date', cell: (e) => formatDate(e.date) },
    { id: 'kind', header: 'Type', cell: (e) => <Badge variant="outline" className={KIND_CLASS[e.kind]}>{KIND_LABEL[e.kind]}</Badge> },
    { id: 'reference', header: 'Reference', cell: (e) => <span className="font-medium">{e.reference}</span> },
    { id: 'party', header: 'Party', cell: (e) => e.party },
    { id: 'detail', header: 'Detail', cell: (e) => <span className="text-muted-foreground">{e.detail ?? ''}</span> },
    { id: 'amount', header: 'Amount', numeric: true, cell: (e) => `₹${e.amountDisplay}` },
  ];

  return (
    <div className="space-y-4">
      <DateRangeBar value={range} onChange={setRange}>
        <DownloadButtons report="day-book" params={{ ...range }} fileName={`day-book-${range.from}-to-${range.to}`} disabled={loading} />
      </DateRangeBar>
      {data ? (
        <div className="flex flex-wrap gap-2" data-report-totals-strip>
          <Stat label="Sales" value={data.totals.salesDisplay} />
          <Stat label="Receipts" value={data.totals.receiptsDisplay} tone="success" />
          <Stat label="Credit notes" value={data.totals.creditNotesDisplay} />
          <Stat label="Purchases" value={data.totals.purchasesDisplay} tone="warning" />
          <Stat label="Expenses" value={data.totals.expensesDisplay} tone="destructive" />
          <Stat label="Net cash" value={data.totals.netCashDisplay} tone={data.totals.netCashPaise < 0 ? 'destructive' : 'success'} />
        </div>
      ) : null}
      <ReportTable
        columns={columns}
        rows={data?.entries}
        rowKey={(e, i) => `${e.kind}-${e.reference}-${i}`}
        loading={loading}
        error={error}
        onRetry={() => void reload()}
        emptyTitle="No vouchers in this period"
        emptyDescription="Sales, receipts, credit notes, purchases and expenses dated in the range will appear here."
        caption="Net cash is receipts minus expenses - purchases sit on supplier credit until they are paid."
      />
    </div>
  );
}

// ------------------------------------------------------- Receivables Ageing

export function ReceivablesAgeingPanel() {
  const [asOf, setAsOf] = useState(today());
  const { data, loading, error, reload } = useAsyncData(() => reportService.receivablesAgeing(asOf), [asOf]);
  const t = data?.totals;

  const columns: ReportColumn<AgeingRow>[] = [
    { id: 'customer', header: 'Customer', cell: (r) => <span className="font-medium">{r.customerName}</span> },
    { id: 'mobile', header: 'Mobile', cell: (r) => <span className="text-muted-foreground">{r.mobileNo}</span> },
    { id: 'bills', header: 'Open bills', numeric: true, cell: (r) => r.openInvoices },
    { id: 'b0', header: '0–30 days', numeric: true, cell: (r) => `₹${r.current0To30Display}`, total: t ? `₹${t.current0To30Display}` : '' },
    { id: 'b31', header: '31–60 days', numeric: true, cell: (r) => `₹${r.days31To60Display}`, total: t ? `₹${t.days31To60Display}` : '' },
    { id: 'b61', header: '61–90 days', numeric: true, cell: (r) => `₹${r.days61To90Display}`, total: t ? `₹${t.days61To90Display}` : '' },
    { id: 'b91', header: 'Over 90', numeric: true, cell: (r) => <span className={r.over90Paise > 0 ? 'text-destructive' : ''}>₹{r.over90Display}</span>, total: t ? `₹${t.over90Display}` : '' },
    { id: 'total', header: 'Total due', numeric: true, cell: (r) => <span className="font-semibold">₹{r.totalDisplay}</span>, total: t ? `₹${t.totalDisplay}` : '' },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end" data-report-filters>
        <FormField id="report-asof" label="As of">
          <DatePicker id="report-asof" value={asOf} max={today()} onChange={setAsOf} />
        </FormField>
        <div className="sm:ml-auto">
          <DownloadButtons report="receivables-ageing" params={{ asOf }} fileName={`receivables-ageing-${asOf}`} disabled={loading} />
        </div>
      </div>
      <ReportNote>
        Age is counted from the invoice date - bills carry no separate due date. Customers with nothing outstanding are not listed.
      </ReportNote>
      <ReportTable
        columns={columns}
        rows={data?.rows}
        rowKey={(r) => r.customerId}
        loading={loading}
        error={error}
        onRetry={() => void reload()}
        emptyTitle="Nothing outstanding"
        emptyDescription="Every bill dated on or before this day has been settled."
        totalsLabel="Total"
      />
    </div>
  );
}

// ----------------------------------------------------------- Stock Valuation

export function StockValuationPanel() {
  const { data, loading, error, reload } = useAsyncData(() => reportService.stockValuation(), []);
  const t = data?.totals;

  const columns: ReportColumn<StockValuationRow>[] = [
    { id: 'code', header: 'Code', cell: (r) => <span className="text-muted-foreground">{r.productCode}</span> },
    { id: 'name', header: 'Product', cell: (r) => <span className="font-medium">{r.productName}</span> },
    { id: 'category', header: 'Category', cell: (r) => r.categoryName ?? '—' },
    { id: 'qty', header: 'Qty on hand', numeric: true, cell: (r) => `${r.quantityOnHand} ${r.unit}` },
    { id: 'rate', header: 'Purchase rate', numeric: true, cell: (r) => `₹${r.purchasePriceDisplay}` },
    { id: 'cost', header: 'Value at cost', numeric: true, cell: (r) => <span className="font-semibold">₹{r.costValueDisplay}</span>, total: t ? `₹${t.costValueDisplay}` : '' },
    { id: 'sell', header: 'Value at selling', numeric: true, cell: (r) => `₹${r.sellingValueDisplay}`, total: t ? `₹${t.sellingValueDisplay}` : '' },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between" data-report-filters>
        <p className="text-sm text-muted-foreground">
          {data ? <>As of {formatDate(data.asOf)} · {data.totals.products} products with stock</> : 'Loading…'}
        </p>
        <DownloadButtons report="stock-valuation" params={{}} fileName={`stock-valuation-${today()}`} disabled={loading} />
      </div>
      <ReportNote>
        Valued at each product&apos;s current purchase price - there is no cost history, so this is today&apos;s shelf at today&apos;s rate, not what it was bought for.
      </ReportNote>
      <ReportTable
        columns={columns}
        rows={data?.rows}
        rowKey={(r) => r.productId}
        loading={loading}
        error={error}
        onRetry={() => void reload()}
        emptyTitle="No stock on hand"
        emptyDescription="Products with a non-zero quantity will appear here."
        totalsLabel={`Total (${t?.products ?? 0} products)`}
      />
    </div>
  );
}

// ---------------------------------------------------------- Purchase Register

export function PurchaseRegisterPanel() {
  const [range, setRange] = useState<DateRange>(defaultRange);
  const { data, loading, error, reload } = useAsyncData(
    () => reportService.purchaseRegister(range.from, range.to), [range.from, range.to],
  );
  const t = data?.totals;

  const columns: ReportColumn<PurchaseRegisterRow>[] = [
    { id: 'date', header: 'Date', cell: (r) => formatDate(r.purchaseDate) },
    { id: 'number', header: 'Purchase no.', cell: (r) => <span className="font-medium">{r.purchaseNumber}</span> },
    { id: 'bill', header: 'Supplier bill', cell: (r) => r.supplierBillNumber ?? '—' },
    { id: 'supplier', header: 'Supplier', cell: (r) => r.supplierName },
    { id: 'gstin', header: 'GSTIN', cell: (r) => <span className="font-mono text-xs">{r.supplierGstNo ?? '—'}</span> },
    { id: 'taxable', header: 'Taxable', numeric: true, cell: (r) => `₹${r.taxableDisplay}`, total: t ? `₹${t.taxableDisplay}` : '' },
    { id: 'cgst', header: 'CGST', numeric: true, cell: (r) => `₹${r.cgstDisplay}`, total: t ? `₹${t.cgstDisplay}` : '' },
    { id: 'sgst', header: 'SGST', numeric: true, cell: (r) => `₹${r.sgstDisplay}`, total: t ? `₹${t.sgstDisplay}` : '' },
    { id: 'igst', header: 'IGST', numeric: true, cell: (r) => `₹${r.igstDisplay}`, total: t ? `₹${t.igstDisplay}` : '' },
    { id: 'total', header: 'Total', numeric: true, cell: (r) => <span className="font-semibold">₹{r.totalDisplay}</span>, total: t ? `₹${t.totalDisplay}` : '' },
    { id: 'balance', header: 'Balance', numeric: true, cell: (r) => <span className={r.balancePaise > 0 ? 'text-warning' : ''}>₹{r.balanceDisplay}</span>, total: t ? `₹${t.balanceDisplay}` : '' },
  ];

  return (
    <div className="space-y-4">
      <DateRangeBar value={range} onChange={setRange}>
        <DownloadButtons report="purchase-register" params={{ ...range }} fileName={`purchase-register-${range.from}-to-${range.to}`} disabled={loading} />
      </DateRangeBar>
      <ReportTable
        columns={columns}
        rows={data?.rows}
        rowKey={(r) => r.purchaseId}
        loading={loading}
        error={error}
        onRetry={() => void reload()}
        emptyTitle="No supplier bills in this period"
        emptyDescription="Received purchases dated in the range will appear here; drafts and cancelled bills are left out."
        totalsLabel={`Total (${t?.bills ?? 0} bills)`}
      />
    </div>
  );
}

// -------------------------------------------------------------- GST Summary

function GstSectionTable({ section, loading, error, onRetry }: {
  section: GstSection | undefined; loading: boolean; error: ReturnType<typeof useAsyncData>['error']; onRetry: () => void;
}) {
  const t = section?.totals;
  const columns: ReportColumn<GstRateRow>[] = [
    { id: 'rate', header: 'Rate', cell: (r) => <span className="font-medium">{r.ratePercent}%</span> },
    { id: 'taxable', header: 'Taxable value', numeric: true, cell: (r) => `₹${r.taxableDisplay}`, total: t ? `₹${t.taxableDisplay}` : '' },
    { id: 'cgst', header: 'CGST', numeric: true, cell: (r) => `₹${r.cgstDisplay}`, total: t ? `₹${t.cgstDisplay}` : '' },
    { id: 'sgst', header: 'SGST', numeric: true, cell: (r) => `₹${r.sgstDisplay}`, total: t ? `₹${t.sgstDisplay}` : '' },
    { id: 'igst', header: 'IGST', numeric: true, cell: (r) => `₹${r.igstDisplay}`, total: t ? `₹${t.igstDisplay}` : '' },
    { id: 'tax', header: 'Total tax', numeric: true, cell: (r) => <span className="font-semibold">₹{r.totalTaxDisplay}</span>, total: t ? `₹${t.totalTaxDisplay}` : '' },
  ];
  return (
    <section className="space-y-2" data-gst-section={section?.title ?? ''}>
      <h3 className="text-sm font-semibold">{section?.title ?? '…'}</h3>
      <ReportTable
        columns={columns}
        rows={section?.rows}
        rowKey={(r, i) => `${r.ratePercent ?? 'x'}-${i}`}
        loading={loading}
        error={error}
        onRetry={onRetry}
        emptyTitle="Nothing in this period"
        totalsLabel="Total"
      />
    </section>
  );
}

export function GstSummaryPanel() {
  const [range, setRange] = useState<DateRange>(defaultRange);
  const { data, loading, error, reload } = useAsyncData(
    () => reportService.gstSummary(range.from, range.to), [range.from, range.to],
  );
  const retry = () => void reload();

  return (
    <div className="space-y-4">
      <DateRangeBar value={range} onChange={setRange}>
        <DownloadButtons report="gst-summary" params={{ ...range }} fileName={`gst-summary-${range.from}-to-${range.to}`} disabled={loading} />
      </DateRangeBar>
      {data ? (
        <div className="flex flex-wrap gap-2" data-report-totals-strip>
          <Stat label="Net CGST" value={data.netCgstDisplay} />
          <Stat label="Net SGST" value={data.netSgstDisplay} />
          <Stat label="Net IGST" value={data.netIgstDisplay} />
          <Stat label="Net payable" value={data.netTaxDisplay} tone={data.netTaxPaise < 0 ? 'success' : 'warning'} />
        </div>
      ) : null}
      <ReportNote>
        Net payable is output tax on sales, less tax on credit notes issued, less input tax on purchases. Input credit is shown as booked - whether it can be claimed depends on the supplier having filed.
      </ReportNote>
      {error ? (
        <GstSectionTable section={undefined} loading={false} error={error} onRetry={retry} />
      ) : (
        <>
          <GstSectionTable section={data?.outward} loading={loading} error={null} onRetry={retry} />
          <GstSectionTable section={data?.creditNotes} loading={loading} error={null} onRetry={retry} />
          <GstSectionTable section={data?.inward} loading={loading} error={null} onRetry={retry} />
        </>
      )}
    </div>
  );
}
