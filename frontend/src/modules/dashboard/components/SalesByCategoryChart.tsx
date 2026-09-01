import { useEffect, useState } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { AlertCircle, PieChartIcon } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { Skeleton } from '@/shared/components/ui/skeleton';
import {
  analyticsService, PERIOD_PRESETS, resolvePeriod, type CategorySlice,
} from '../services/analyticsService';

/**
 * Where the revenue came from, by category (CR-048's own DTO comment on
 * CategorySlice already called this out - "a bar, or a donut segment" -
 * but no chart ever consumed /v1/analytics/sales-by-category until now).
 * A trend answers "which way is the shop heading" (SalesTrendChart, a
 * line); this answers "what makes up today's total" - a proportion
 * question, which is exactly what a donut is for and a line is not.
 *
 * Same period preset as the trend chart, same tenant-scoped, server-
 * aggregated data - nothing computed or invented in the browser.
 */
export function SalesByCategoryChart() {
  const [preset, setPreset] = useState(PERIOD_PRESETS[1]);
  const [slices, setSlices] = useState<CategorySlice[] | null>(null);
  const [summary, setSummary] = useState('');
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    const { from, to } = resolvePeriod(preset);

    analyticsService.salesByCategory(from, to)
      .then((result) => {
        if (cancelled) return;
        setSlices(result.slices);
        setSummary(result.summary);
      })
      .catch(() => { if (!cancelled) setFailed(true); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [preset]);

  const isEmpty = !loading && !failed && (slices?.length ?? 0) === 0;

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4 space-y-0 pb-2">
        <div>
          <CardTitle className="text-base">Sales by category</CardTitle>
          <p className="mt-0.5 text-xs text-muted-foreground">Share of revenue this period</p>
        </div>

        <div className="flex shrink-0 overflow-hidden rounded-md border" role="group" aria-label="Period">
          {PERIOD_PRESETS.map((option) => (
            <button
              key={option.id}
              type="button"
              aria-pressed={option.id === preset.id}
              onClick={() => setPreset(option)}
              className={
                option.id === preset.id
                  ? 'bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground'
                  : 'px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground'
              }
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
          <State icon={AlertCircle} title="Could not load the category breakdown"
                 detail="The figures could not be fetched. Try another period, or reload the page." />
        ) : isEmpty ? (
          <State icon={PieChartIcon} title="No sales in this period"
                 detail="Raise an invoice, or choose a wider period above." />
        ) : (
          <>
            <div className="h-[260px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={slices ?? []}
                    dataKey="amountPaise"
                    nameKey="label"
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={95}
                    paddingAngle={2}
                    stroke="hsl(var(--card))"
                    strokeWidth={2}
                  >
                    {(slices ?? []).map((slice, index) => (
                      // Cycles through the same 5 chart tokens every other
                      // chart in the app already uses - no second palette.
                      <Cell key={slice.label} fill={`hsl(var(--chart-${(index % 5) + 1}))`} />
                    ))}
                  </Pie>
                  <Tooltip
                    content={({ active, payload }) => {
                      if (!active || !payload?.length) return null;
                      const slice = payload[0].payload as CategorySlice;
                      return (
                        <div className="surface-overlay rounded-md border px-3 py-2 text-xs">
                          <p className="font-medium">{slice.label}</p>
                          <p className="tabular mt-0.5">₹{slice.amountDisplay}</p>
                          <p className="mt-0.5 text-muted-foreground">{slice.sharePercent}% of revenue</p>
                        </div>
                      );
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>

            {/* Legend as a real list, not chart-only colour - readable at a
                glance and the accessible path a screen reader can use. */}
            <ul className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1.5 border-t pt-3 text-xs sm:grid-cols-3">
              {(slices ?? []).map((slice, index) => (
                <li key={slice.label} className="flex items-center gap-1.5 truncate">
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ background: `hsl(var(--chart-${(index % 5) + 1}))` }}
                    aria-hidden
                  />
                  <span className="truncate text-muted-foreground">{slice.label}</span>
                  <span className="ml-auto shrink-0 font-medium">{slice.sharePercent}%</span>
                </li>
              ))}
            </ul>

            {summary ? (
              <p className="mt-3 border-t pt-3 text-xs text-muted-foreground">{summary}</p>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function State({ icon: Icon, title, detail }: {
  icon: typeof PieChartIcon; title: string; detail: string;
}) {
  return (
    <div className="flex h-[260px] flex-col items-center justify-center gap-1.5 text-center">
      <Icon className="h-6 w-6 text-muted-foreground" aria-hidden />
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-xs text-xs text-muted-foreground">{detail}</p>
    </div>
  );
}
