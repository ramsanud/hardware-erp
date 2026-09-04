import { useEffect, useMemo, useState } from 'react';
import {
  Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { AlertCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { platformAdminBillingService } from '../services/platformAdminBillingService';
import type { PlatformBillingOverviewResponse } from '../types';

/**
 * CR-057 phase 9 - replaces the Overview page's previous "no billing
 * gateway connected yet" placeholder with real monthly revenue, aggregated
 * server-side from platform_subscription_payment (spec's own "no fake
 * production metrics" rule). razorpayConfigured being false is shown
 * honestly rather than rendering a chart of real zeros that reads as "no
 * revenue this month" when actually no gateway exists at all.
 */
export function PlatformRevenueChart() {
  const [data, setData] = useState<PlatformBillingOverviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    platformAdminBillingService.overview()
      .then((result) => { if (!cancelled) setData(result); })
      .catch(() => { if (!cancelled) setFailed(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const points = useMemo(() => (data?.monthly ?? []).map((p) => ({
    ...p,
    rupees: p.revenuePaise / 100,
    label: formatMonth(p.month),
  })), [data]);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Revenue</CardTitle>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Last 12 months, captured Razorpay payments only - not self-declared plan changes.
        </p>
      </CardHeader>
      <CardContent>
        {loading ? (
          <Skeleton className="h-[240px] w-full" />
        ) : failed ? (
          <State icon={AlertCircle} title="Could not load revenue" detail="Reload the page to try again." />
        ) : !data?.razorpayConfigured ? (
          <Alert variant="warning">
            <AlertDescription>
              Billing is not configured in this environment - no Razorpay keys are set, so there is no
              revenue to report yet. This is not the same as zero revenue.
            </AlertDescription>
          </Alert>
        ) : (
          <>
            <div className="mb-4 grid grid-cols-3 gap-4 text-center">
              <div>
                <p className="tabular text-xl font-semibold">₹{(data.totalRevenuePaiseLast12Months / 100).toLocaleString('en-IN')}</p>
                <p className="text-xs text-muted-foreground">Total (12mo)</p>
              </div>
              <div>
                <p className="tabular text-xl font-semibold text-success">{data.successfulPaymentsLast12Months}</p>
                <p className="text-xs text-muted-foreground">Successful payments</p>
              </div>
              <div>
                <p className="tabular text-xl font-semibold text-destructive">{data.failedPaymentsLast12Months}</p>
                <p className="text-xs text-muted-foreground">Failed payments</p>
              </div>
            </div>
            <div className="h-[220px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={points} margin={{ top: 6, right: 6, left: -12, bottom: 0 }}>
                  <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                         tickLine={false} axisLine={{ stroke: 'hsl(var(--border))' }} minTickGap={16} />
                  <YAxis tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                         tickLine={false} axisLine={false} width={64} tickFormatter={compactRupees} />
                  <Tooltip
                    cursor={{ fill: 'hsl(var(--muted))' }}
                    content={({ active, payload }) => {
                      if (!active || !payload?.length) return null;
                      const p = payload[0].payload as (typeof points)[number];
                      return (
                        <div className="surface-overlay rounded-md border px-3 py-2 text-xs">
                          <p className="font-medium">{p.label}</p>
                          <p className="tabular mt-0.5">₹{p.rupees.toLocaleString('en-IN')}</p>
                          <p className="mt-0.5 text-muted-foreground">
                            {p.successfulCount} successful, {p.failedCount} failed
                          </p>
                        </div>
                      );
                    }}
                  />
                  <Bar dataKey="rupees" fill="hsl(var(--chart-1))" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function State({ icon: Icon, title, detail }: { icon: typeof AlertCircle; title: string; detail: string }) {
  return (
    <div className="flex h-[240px] flex-col items-center justify-center gap-1.5 text-center">
      <Icon className="h-6 w-6 text-muted-foreground" aria-hidden />
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-xs text-xs text-muted-foreground">{detail}</p>
    </div>
  );
}

function compactRupees(value: number): string {
  if (value >= 10_000_000) return `₹${(value / 10_000_000).toFixed(1)}Cr`;
  if (value >= 100_000) return `₹${(value / 100_000).toFixed(1)}L`;
  if (value >= 1_000) return `₹${Math.round(value / 1_000)}K`;
  return `₹${value}`;
}

function formatMonth(month: string): string {
  const date = new Date(`${month}-01T00:00:00`);
  if (Number.isNaN(date.getTime())) return month;
  return date.toLocaleDateString('en-IN', { month: 'short', year: '2-digit' });
}
