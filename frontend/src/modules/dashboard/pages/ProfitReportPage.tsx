import { useCallback, useEffect, useState } from 'react';
import { Loader2, TrendingUp } from 'lucide-react';
import { DatePicker } from '@/shared/components/ui/date-picker';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { FormField } from '@/shared/components/FormField';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ApiError } from '@/shared/types/api';
import { analyticsService, type ProfitResponse } from '../services/analyticsService';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function firstOfMonth(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
}

interface Line {
  label: string;
  display: string;
  paise: number;
  tone?: 'subtract' | 'result';
  note?: string;
}

/**
 * CR-091 Phase 6. A plain profit statement for a date range. Every figure
 * is one the server actually recorded: revenue and returns from invoices
 * and credit notes, COGS from the cost frozen on each invoice line when it
 * was sold, expenses from the expense ledger. Nothing here is estimated
 * from today's purchase price.
 */
export function ProfitReportPage() {
  const [fromDate, setFromDate] = useState(firstOfMonth());
  const [toDate, setToDate] = useState(today());
  const [profit, setProfit] = useState<ProfitResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProfit(await analyticsService.profit(fromDate, toDate));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError({ message: 'Could not load the profit report', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [fromDate, toDate]);

  useEffect(() => { void reload(); }, [reload]);

  const lines: Line[] = profit ? [
    { label: 'Sales (invoices)', display: profit.revenueDisplay, paise: profit.revenuePaise },
    { label: 'Less: sales returns (credit notes)', display: profit.salesReturnDisplay, paise: profit.salesReturnPaise, tone: 'subtract' },
    { label: 'Net revenue', display: profit.netRevenueDisplay, paise: profit.netRevenuePaise, tone: 'result' },
    { label: 'Less: cost of goods sold', display: profit.cogsDisplay, paise: profit.cogsPaise, tone: 'subtract', note: 'At the cost each item carried when it was sold, net of returned goods.' },
    { label: 'Gross profit', display: profit.grossProfitDisplay, paise: profit.grossProfitPaise, tone: 'result' },
    { label: 'Less: business expenses', display: profit.expenseDisplay, paise: profit.expensePaise, tone: 'subtract' },
    { label: 'Net profit', display: profit.netProfitDisplay, paise: profit.netProfitPaise, tone: 'result' },
  ] : [];

  return (
    <>
      <PageHeader
        title="Profit & loss"
        description="Revenue, cost of goods sold and expenses for a period - computed from what was actually recorded, not estimated."
      />

      <Card className="max-w-2xl">
        <CardHeader>
          <div className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-primary" aria-hidden />
            <CardTitle className="text-base">Statement</CardTitle>
          </div>
          <CardDescription>
            Cost of goods sold uses the weighted-average purchase cost frozen on each invoice line at the
            moment of sale. Changing a product&apos;s purchase price later never changes a profit already recorded.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="fromDate" label="From date">
              <DatePicker id="fromDate" value={fromDate} max={toDate} onChange={setFromDate} />
            </FormField>
            <FormField id="toDate" label="To date">
              <DatePicker id="toDate" value={toDate} min={fromDate} max={today()} onChange={setToDate} />
            </FormField>
          </div>

          {error ? (
            <ErrorState error={error} onRetry={() => void reload()} />
          ) : loading && !profit ? (
            <div className="flex items-center justify-center py-8 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" aria-label="Loading" />
            </div>
          ) : profit ? (
            <dl className="divide-y">
              {lines.map((line) => (
                <div
                  key={line.label}
                  className={`flex items-start justify-between gap-4 py-2 text-sm ${line.tone === 'result' ? 'font-semibold' : ''}`}
                >
                  <dt className={line.tone === 'subtract' ? 'pl-4 text-muted-foreground' : ''}>
                    {line.label}
                    {line.note ? <span className="block text-xs font-normal text-muted-foreground">{line.note}</span> : null}
                  </dt>
                  <dd className={`tabular whitespace-nowrap ${line.tone === 'result' && line.paise < 0 ? 'text-destructive' : ''}`}>
                    {line.tone === 'subtract' ? '- ' : ''}₹{line.display}
                  </dd>
                </div>
              ))}
            </dl>
          ) : null}
        </CardContent>
      </Card>
    </>
  );
}
