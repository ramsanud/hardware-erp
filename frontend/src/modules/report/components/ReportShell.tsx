import { useState } from 'react';
import type { ReactNode } from 'react';
import { FileDown, FileSpreadsheet, Inbox, Share2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { DatePicker } from '@/shared/components/ui/date-picker';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Card, CardContent } from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { cn, downloadBlob } from '@/shared/lib/utils';
import { DocumentShareModal } from '@/modules/document/components/DocumentShareModal';
import { reportService } from '../services/reportService';
import type { ReportFormat } from '../types';

/*
  CR-086. The pieces every report screen shares: a date-range bar with the
  presets a shop actually asks for, the two download buttons, and a table
  that knows how to draw a totals row. Each report page is then just its
  columns and its rows.
*/

// ------------------------------------------------------------------ dates

function iso(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

export function today(): string {
  return iso(new Date());
}

export interface DateRange { from: string; to: string }

/** Indian financial year runs April to March. */
export const RANGE_PRESETS: { id: string; label: string; range: () => DateRange }[] = [
  { id: 'today', label: 'Today', range: () => ({ from: today(), to: today() }) },
  {
    id: 'this-month', label: 'This month',
    range: () => { const n = new Date(); return { from: iso(new Date(n.getFullYear(), n.getMonth(), 1)), to: iso(n) }; },
  },
  {
    id: 'last-month', label: 'Last month',
    range: () => {
      const n = new Date();
      return { from: iso(new Date(n.getFullYear(), n.getMonth() - 1, 1)), to: iso(new Date(n.getFullYear(), n.getMonth(), 0)) };
    },
  },
  {
    id: 'this-quarter', label: 'This quarter',
    range: () => { const n = new Date(); const q = Math.floor(n.getMonth() / 3) * 3; return { from: iso(new Date(n.getFullYear(), q, 1)), to: iso(n) }; },
  },
  {
    id: 'this-fy', label: 'This FY',
    range: () => { const n = new Date(); const y = n.getMonth() >= 3 ? n.getFullYear() : n.getFullYear() - 1; return { from: iso(new Date(y, 3, 1)), to: iso(n) }; },
  },
];

export function defaultRange(): DateRange {
  return RANGE_PRESETS[1].range();
}

interface DateRangeBarProps {
  value: DateRange;
  onChange: (range: DateRange) => void;
  children?: ReactNode;
}

export function DateRangeBar({ value, onChange, children }: DateRangeBarProps) {
  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-end" data-report-filters>
      <div className="grid grid-cols-2 gap-3 sm:max-w-md">
        <FormField id="report-from" label="From">
          <DatePicker id="report-from" value={value.from} max={value.to} onChange={(from) => onChange({ ...value, from })} />
        </FormField>
        <FormField id="report-to" label="To">
          <DatePicker id="report-to" value={value.to} min={value.from} max={today()} onChange={(to) => onChange({ ...value, to })} />
        </FormField>
      </div>
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Quick ranges">
        {RANGE_PRESETS.map((preset) => {
          const r = preset.range();
          const active = r.from === value.from && r.to === value.to;
          return (
            <Button
              key={preset.id}
              type="button"
              size="sm"
              variant={active ? 'secondary' : 'ghost'}
              className={cn('h-8', active && 'ring-1 ring-primary/30')}
              onClick={() => onChange(r)}
              data-range-preset={preset.id}
            >
              {preset.label}
            </Button>
          );
        })}
      </div>
      {children ? <div className="lg:ml-auto">{children}</div> : null}
    </div>
  );
}

// -------------------------------------------------------------- downloads

interface DownloadButtonsProps {
  report: string;
  params: Record<string, string>;
  /** Base of the saved file name; the format's extension is appended. */
  fileName: string;
  disabled?: boolean;
}

export function DownloadButtons({ report, params, fileName, disabled }: DownloadButtonsProps) {
  const [busy, setBusy] = useState<ReportFormat | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [shareOpen, setShareOpen] = useState(false);

  const download = async (format: ReportFormat) => {
    setError(null);
    setBusy(format);
    try {
      const blob = await reportService.export(report, format, params);
      downloadBlob(blob, `${fileName}.${format}`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not build the file.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-2" data-report-downloads>
      <Button type="button" variant="outline" size="sm" disabled={disabled || busy !== null} loading={busy === 'pdf'} onClick={() => void download('pdf')}>
        <FileDown className="h-4 w-4" aria-hidden />
        PDF
      </Button>
      <Button type="button" variant="outline" size="sm" disabled={disabled || busy !== null} loading={busy === 'xlsx'} onClick={() => void download('xlsx')}>
        <FileSpreadsheet className="h-4 w-4" aria-hidden />
        Excel
      </Button>
      {/* CR-101 - the background queue: PDF, image or Excel, then WhatsApp / email / download. */}
      <Button type="button" variant="outline" size="sm" disabled={disabled} onClick={() => setShareOpen(true)} data-report-share>
        <Share2 className="h-4 w-4" aria-hidden />
        Share
      </Button>
      {error ? <span className="text-xs text-destructive" role="alert">{error}</span> : null}
      <DocumentShareModal
        open={shareOpen}
        onOpenChange={setShareOpen}
        source={{ reportType: report.toUpperCase().replace(/-/g, '_'), params, label: fileName }}
      />
    </div>
  );
}

// ------------------------------------------------------------------ table

export interface ReportColumn<Row> {
  id: string;
  header: string;
  /** Right-aligned, tabular figures. */
  numeric?: boolean;
  cell: (row: Row) => ReactNode;
  /** The figure for this column in the totals row; omit for a blank cell. */
  total?: ReactNode;
  className?: string;
}

interface ReportTableProps<Row> {
  columns: ReportColumn<Row>[];
  rows: Row[] | undefined;
  rowKey: (row: Row, index: number) => string | number;
  loading: boolean;
  error: ApiError | null;
  onRetry: () => void;
  emptyTitle: string;
  emptyDescription?: string;
  /** Label in the first cell of the totals row. Omit to draw no totals row. */
  totalsLabel?: string;
  caption?: ReactNode;
}

export function ReportTable<Row>({
  columns, rows, rowKey, loading, error, onRetry, emptyTitle, emptyDescription, totalsLabel, caption,
}: ReportTableProps<Row>) {
  if (error) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }
  const empty = !loading && rows !== undefined && rows.length === 0;
  return (
    <Card>
      {caption ? <div className="border-b px-4 py-2.5 text-sm text-muted-foreground" data-report-caption>{caption}</div> : null}
      <CardContent className="p-0">
        {empty ? (
          <EmptyState icon={Inbox} title={emptyTitle} description={emptyDescription} />
        ) : (
          <Table data-report-table>
            <TableHeader>
              <TableRow>
                {columns.map((column) => (
                  <TableHead key={column.id} className={cn(column.numeric && 'text-right', column.className)}>
                    {column.header}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            {loading || rows === undefined ? (
              <TableSkeleton columns={columns.length} rows={6} />
            ) : (
              <TableBody>
                {rows.map((row, index) => (
                  <TableRow key={rowKey(row, index)}>
                    {columns.map((column) => (
                      <TableCell key={column.id} className={cn(column.numeric && 'text-right tabular-nums', column.className)}>
                        {column.cell(row)}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
                {totalsLabel ? (
                  <TableRow className="bg-muted/40 font-semibold" data-report-totals>
                    {columns.map((column, index) => (
                      <TableCell key={column.id} label="" className={cn(column.numeric && 'text-right tabular-nums', column.className)}>
                        {index === 0 ? totalsLabel : column.total ?? ''}
                      </TableCell>
                    ))}
                  </TableRow>
                ) : null}
              </TableBody>
            )}
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

/** A one-line honesty note above a report - what the figures are and are not. */
export function ReportNote({ children }: { children: ReactNode }) {
  return (
    <Alert>
      <AlertDescription className="text-xs">{children}</AlertDescription>
    </Alert>
  );
}
