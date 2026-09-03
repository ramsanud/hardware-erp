import { useEffect, useState } from 'react';
import {
  Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { Download, Loader2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { downloadBlob } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { platformAdminAnalyticsService } from '../services/platformAdminAnalyticsService';
import type { TenantAnalyticsResponse } from '../types';

/**
 * CR-057 phase 10 - every series here comes from
 * TenantAnalyticsService.overview()'s own real aggregate queries (see its
 * javadoc for exactly what churn/module-usage are derived from and their
 * stated limitations) - nothing is computed or invented in the browser.
 */
export function PlatformAdminAnalyticsPage() {
  const { admin } = usePlatformAdminAuth();
  const canExport = admin?.permissions.includes('ANALYTICS_EXPORT') ?? false;

  const [data, setData] = useState<TenantAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [exporting, setExporting] = useState<'csv' | 'xlsx' | 'pdf' | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await platformAdminAnalyticsService.overview());
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const handleExport = async (format: 'csv' | 'xlsx' | 'pdf') => {
    setExporting(format);
    try {
      const blob = await platformAdminAnalyticsService.export(format);
      downloadBlob(blob, `tenant-analytics.${format}`);
    } finally {
      setExporting(null);
    }
  };

  if (error) return <Card><ErrorState error={error} onRetry={load} /></Card>;
  if (loading || !data) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <PageHeader
        title="Analytics"
        description={`Growth, module adoption and churn across ${data.activeTenantsNow} active tenants`}
        actions={canExport ? (
          <div className="flex gap-2">
            {(['csv', 'xlsx', 'pdf'] as const).map((format) => (
              <Button key={format} variant="outline" size="sm" loading={exporting === format}
                      onClick={() => handleExport(format)}>
                <Download className="h-3.5 w-3.5" />
                {format.toUpperCase()}
              </Button>
            ))}
          </div>
        ) : null}
      />

      <Card>
        <CardHeader><CardTitle className="text-base">Tenant &amp; user growth</CardTitle></CardHeader>
        <CardContent>
          <div className="h-[260px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data.growth} margin={{ top: 6, right: 6, left: -12, bottom: 0 }}>
                <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="month" tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                       tickLine={false} axisLine={{ stroke: 'hsl(var(--border))' }} minTickGap={16} />
                <YAxis tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} tickLine={false} axisLine={false} width={40} />
                <Tooltip contentStyle={{ fontSize: 12 }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Line type="monotone" dataKey="newTenants" name="New tenants" stroke="hsl(var(--chart-1))" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="newUsers" name="New users" stroke="hsl(var(--chart-2))" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="activeUsers" name="Active users" stroke="hsl(var(--chart-3))" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <p className="mt-3 border-t pt-3 text-xs text-muted-foreground">
            Active users is a real distinct-login count per month, not app_user's last-login snapshot.
          </p>
        </CardContent>
      </Card>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="text-base">Module adoption</CardTitle></CardHeader>
          <CardContent>
            <div className="h-[260px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.moduleUsage} layout="vertical" margin={{ top: 6, right: 12, left: 8, bottom: 0 }}>
                  <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} tickLine={false} axisLine={false} />
                  <YAxis type="category" dataKey="module" width={80} tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} tickLine={false} axisLine={false} />
                  <Tooltip
                    contentStyle={{ fontSize: 12 }}
                    formatter={(value, _name, entry) => [
                      `${value} tenants (${(entry.payload as { adoptionPercent: number }).adoptionPercent.toFixed(1)}%)`, 'Using',
                    ]}
                  />
                  <Bar dataKey="tenantsUsing" fill="hsl(var(--chart-1))" radius={[0, 3, 3, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <p className="mt-3 border-t pt-3 text-xs text-muted-foreground">
              Of {data.activeTenantsNow} currently active tenants, how many have at least one record in each module.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Churn</CardTitle></CardHeader>
          <CardContent>
            <div className="h-[260px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.churn} margin={{ top: 6, right: 6, left: -12, bottom: 0 }}>
                  <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="month" tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                         tickLine={false} axisLine={{ stroke: 'hsl(var(--border))' }} minTickGap={16} />
                  <YAxis tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} tickLine={false} axisLine={false} width={40}
                         tickFormatter={(v: number) => `${v}%`} />
                  <Tooltip
                    contentStyle={{ fontSize: 12 }}
                    formatter={(value) => [value == null ? 'n/a' : `${Number(value).toFixed(1)}%`, 'Churn rate']}
                  />
                  <Bar dataKey="churnRatePercent" name="Churn rate" fill="hsl(var(--destructive))" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <p className="mt-3 border-t pt-3 text-xs text-muted-foreground">
              Approximation: tenants suspended this month &divide; total tenants that exist by month end -
              not a cohort or usage-based churn measure.
            </p>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
