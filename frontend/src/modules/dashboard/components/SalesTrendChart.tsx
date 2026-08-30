import { useEffect, useMemo, useState } from 'react';
import {
  Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { AlertCircle, TrendingUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { cn } from '@/shared/lib/utils';
import {
  analyticsService, PERIOD_PRESETS, resolvePeriod,
  type PeriodPreset, type TrendSeries,
} from '../services/analyticsService';

/**
 * Revenue over time (CR-048). A line/area chart, because the question is
 * "which way is the shop heading" - a trend over time, which is the one
 * question a bar or donut cannot answer.
 *
 * Every figure is fetched from /v1/analytics/revenue-trend, which aggregates
 * with GROUP BY in PostgreSQL and is tenant-scoped from the caller's JWT.
 * Nothing here is computed, sampled or invented in the browser.
 */
export function SalesTrendChart() {
  const [preset, setPreset] = useState<PeriodPreset>(PERIOD_PRESETS[1]);
  const [data, setData] = useState<TrendSeries | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    const { from, to } = resolvePeriod(preset);

    analyticsService.revenueTrend(from, to, preset.granularity)
      .then((result) => { if (!cancelled) setData(result); })
      .catch(() => { if (!cancelled) setFailed(true); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [preset]);

  /**
   * Rupees, not paise, for the axis - recharts needs a number it can scale,
   * and an axis in paise reads as an order of magnitude too large. The
   * tooltip still shows the server's formatted string, so the precise figure
   * is never the browser's arithmetic.
   */
  const points = useMemo(() => (data?.points ?? []).map((p) => ({
    ...p,
    rupees: p.revenuePaise / 100,
    label: formatBucket(p.bucket, preset.granularity),
  })), [data, preset.granularity]);

  const isEmpty = !loading && !failed && points.length === 0;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4 space-y-0 pb-2">
        <div>
          <CardTitle className="text-base">Sales trend</CardTitle>
          <p className="mt-0.5 text-xs text-muted-foreground">Revenue from invoices raised</p>
        </div>

        <div className="flex shrink-0 overflow-hidden rounded-md border" role="group" aria-label="Period">
          {PERIOD_PRESETS.map((option) => (
            <button
              key={option.id}
              type="button"
              aria-pressed={option.id === preset.id}
              onClick={() => setPreset(option)}
              className={cn(
                'px-2.5 py-1 text-xs font-medium transition-colors',
                option.id === preset.id
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </CardHeader>

      <CardContent>
        {loading ? (
          <Skeleton className="h-[260px] w-full" />
        ) : failed ? (
          <State icon={AlertCircle}
                 title="Could not load the sales trend"
                 detail="The figures could not be fetched. Try another period, or reload the page." />
        ) : isEmpty ? (
          <State icon={TrendingUp}
                 title="No sales in this period"
                 detail="Raise an invoice, or choose a wider period above." />
        ) : (
          <>
            {/* h-[260px] on the wrapper, not the chart: ResponsiveContainer
                measures its parent, and a percentage height inside an
                auto-height parent collapses to zero. */}
            <div className="h-[260px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={points} margin={{ top: 6, right: 6, left: -12, bottom: 0 }}>
                  <defs>
                    <linearGradient id="salesTrendFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="hsl(var(--chart-1))" stopOpacity={0.28} />
                      <stop offset="100%" stopColor="hsl(var(--chart-1))" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>

                  {/* Every colour is a design token, so light, dark and every
                      design style are handled without a second palette. */}
                  <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="label"
                    tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                    tickLine={false}
                    axisLine={{ stroke: 'hsl(var(--border))' }}
                    minTickGap={16}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                    tickLine={false}
                    axisLine={false}
                    width={64}
                    tickFormatter={compactRupees}
                  />
                  <Tooltip
                    cursor={{ stroke: 'hsl(var(--border))' }}
                    content={({ active, payload }) => {
                      if (!active || !payload?.length) return null;
                      const p = payload[0].payload as (typeof points)[number];
                      return (
                        <div className="surface-overlay rounded-md border px-3 py-2 text-xs">
                          <p className="font-medium">{p.label}</p>
                          <p className="tabular mt-0.5">₹{p.revenueDisplay}</p>
                          <p className="mt-0.5 text-muted-foreground">
                            {p.invoiceCount} invoice{p.invoiceCount === 1 ? '' : 's'}
                          </p>
                        </div>
                      );
                    }}
                  />
                  <Area
                    type="monotone"
                    dataKey="rupees"
                    stroke="hsl(var(--chart-1))"
                    strokeWidth={2}
                    fill="url(#salesTrendFill)"
                    // A single data point draws no line, so the dot is what
                    // makes a one-bucket period visible at all.
                    dot={points.length === 1 ? { r: 3 } : false}
                    activeDot={{ r: 4 }}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            {/* The accessible alternative. Not sr-only: the sentence is the
                fastest way for anyone to read the chart, sighted or not. */}
            {data?.summary ? (
              <p className="mt-3 border-t pt-3 text-xs text-muted-foreground">{data.summary}</p>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function State({ icon: Icon, title, detail }: {
  icon: typeof TrendingUp; title: string; detail: string;
}) {
  return (
    <div className="flex h-[260px] flex-col items-center justify-center gap-1.5 text-center">
      <Icon className="h-6 w-6 text-muted-foreground" aria-hidden />
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-xs text-xs text-muted-foreground">{detail}</p>
    </div>
  );
}

/** ₹1.2L / ₹45K - an axis has no room for full Indian digit grouping. */
function compactRupees(value: number): string {
  if (value >= 10_000_000) return `₹${(value / 10_000_000).toFixed(1)}Cr`;
  if (value >= 100_000) return `₹${(value / 100_000).toFixed(1)}L`;
  if (value >= 1_000) return `₹${Math.round(value / 1_000)}K`;
  return `₹${value}`;
}

/** Day buckets read as "12 Aug"; month buckets as "Aug 26". */
function formatBucket(bucket: string, granularity: string): string {
  const date = new Date(`${bucket}T00:00:00`);
  if (Number.isNaN(date.getTime())) return bucket;
  return granularity === 'month'
    ? date.toLocaleDateString('en-IN', { month: 'short', year: '2-digit' })
    : date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}
