import { useState } from 'react';
import { FileJson } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { ErrorState } from '@/shared/components/ErrorState';
import { useAsyncData } from '@/shared/hooks/useAsyncData';
import { ApiError } from '@/shared/types/api';
import { downloadBlob } from '@/shared/lib/utils';
import { reportService } from '../services/reportService';
import { ReportNote } from './ReportShell';

/*
  CR-087. A return period, a count of what will be in the file, and the
  download. The counts are read from the same document that is downloaded,
  so "3 registered buyers" is never a guess about the file.
*/

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** The last 12 completed-or-current months as MMYYYY, newest first. */
function periods(): { value: string; label: string }[] {
  const now = new Date();
  const out: { value: string; label: string }[] = [];
  for (let i = 0; i < 12; i += 1) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    out.push({ value: `${mm}${d.getFullYear()}`, label: `${MONTHS[d.getMonth()]} ${d.getFullYear()}` });
  }
  return out;
}

function count(items: { inv?: unknown[]; nt?: unknown[] }[] | undefined, key: 'inv' | 'nt'): number {
  return (items ?? []).reduce((n, group) => n + ((group[key] as unknown[] | undefined)?.length ?? 0), 0);
}

export function Gstr1Panel() {
  const options = periods();
  const [period, setPeriod] = useState(options[0].value);
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const { data, loading, error, reload } = useAsyncData(() => reportService.gstr1(period), [period]);

  const download = async () => {
    setDownloadError(null);
    setDownloading(true);
    try {
      downloadBlob(await reportService.gstr1File(period), `GSTR1-${period}.json`);
    } catch (caught) {
      setDownloadError(caught instanceof ApiError ? caught.message : 'Could not build the file.');
    } finally {
      setDownloading(false);
    }
  };

  const rows: { label: string; value: number; hint: string }[] = data ? [
    { label: 'B2B invoices', value: count(data.b2b, 'inv'), hint: `${data.b2b.length} registered buyers` },
    { label: 'B2CL invoices', value: count(data.b2cl, 'inv'), hint: 'inter-state, unregistered, above the large-invoice limit' },
    { label: 'B2CS lines', value: data.b2cs.length, hint: 'small consumer sales, summarised by state and rate' },
    { label: 'Credit notes (registered)', value: count(data.cdnr, 'nt'), hint: `${data.cdnr.length} buyers` },
    { label: 'Credit notes (unregistered)', value: data.cdnur.length, hint: 'B2CL reversals; the rest are netted into B2CS' },
    { label: 'HSN lines', value: data.hsn.data.length, hint: 'by HSN, rate and unit' },
  ] : [];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end" data-report-filters>
        <FormField id="gstr1-period" label="Return period">
          <Select value={period} onValueChange={setPeriod}>
            <SelectTrigger id="gstr1-period" className="w-44"><SelectValue /></SelectTrigger>
            <SelectContent>
              {options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
            </SelectContent>
          </Select>
        </FormField>
        <div className="sm:ml-auto">
          <Button type="button" onClick={() => void download()} loading={downloading} disabled={loading || !!error} data-gstr1-download>
            <FileJson className="h-4 w-4" aria-hidden />
            Download GSTR-1 JSON
          </Button>
        </div>
      </div>
      {downloadError ? <p className="text-xs text-destructive" role="alert">{downloadError}</p> : null}
      <ReportNote>
        Built for the GST portal&apos;s offline tool: import the file there, review, then file. The shop&apos;s GSTIN in Settings must be
        valid, and a buyer is treated as registered only when their GSTIN passes the checksum. Exports, advances and nil-rated
        supplies are not covered - this app has no such flows.
      </ReportNote>
      {error ? (
        <ErrorState error={error} onRetry={() => void reload()} />
      ) : (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{data ? `GSTIN ${data.gstin} · period ${data.fp}` : 'Preparing…'}</CardTitle>
            <CardDescription>What the file for this period will contain.</CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" data-gstr1-summary>
              {rows.map((r) => (
                <div key={r.label} className="rounded-lg border bg-card px-3 py-2">
                  <dt className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">{r.label}</dt>
                  <dd className="mt-0.5 text-xl font-semibold tabular-nums">{r.value}</dd>
                  <dd className="text-xs text-muted-foreground">{r.hint}</dd>
                </div>
              ))}
              {loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
            </dl>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
