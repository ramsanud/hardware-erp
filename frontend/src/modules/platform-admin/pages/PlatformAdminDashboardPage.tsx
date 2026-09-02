import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Building2, CreditCard, Database, IndianRupee, Loader2,
  ShoppingCart, TrendingDown, TrendingUp, Users,
} from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { formatDateTime } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { platformAdminDashboardService } from '../services/platformAdminTenantService';
import type { PlatformDashboardResponse } from '../types';

/**
 * Every number on this page comes from PlatformAdminDashboardService's own
 * live database aggregates (see its javadoc) - nothing here is a
 * placeholder. System health/error rate/background job health are a later
 * phase and deliberately not shown yet rather than faked - see
 * platformHealth on the response for the one thing this phase can
 * honestly report.
 */
export function PlatformAdminDashboardPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<PlatformDashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await platformAdminDashboardService.overview());
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

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
        title="Overview"
        description={`What's happening across Hardware ERP right now · updated ${formatDateTime(data.generatedAt)}`}
      />

      <div className="flex items-center gap-2">
        <Badge variant={data.platformHealth.databaseReachable ? 'default' : 'destructive'} className="gap-1.5">
          <Database className="h-3 w-3" />
          Database {data.platformHealth.databaseReachable ? 'reachable' : 'unreachable'}
        </Badge>
        <span className="text-xs text-muted-foreground">
          Full system health, error monitoring and background job status arrive with the System Health Center phase.
        </span>
      </div>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted-foreground">Tenants</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <KpiCard icon={Building2} label="Total tenants" value={data.tenants.total} onClick={() => navigate(PLATFORM_ADMIN_ROUTES.tenants)} />
          <KpiCard icon={Building2} label="Active" value={data.tenants.active} tone="success" />
          <KpiCard icon={Building2} label="Suspended" value={data.tenants.suspended} tone={data.tenants.suspended > 0 ? 'danger' : undefined} />
          <KpiCard
            icon={Building2}
            label="New this month"
            value={data.tenants.newThisMonth}
            trend={data.tenants.growthPercentVsLastMonth}
          />
        </div>
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted-foreground">Users</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <KpiCard icon={Users} label="Total users" value={data.users.total} />
          <KpiCard icon={Users} label="Active" value={data.users.active} tone="success" />
          <KpiCard icon={Users} label="New today" value={data.users.newToday} />
        </div>
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted-foreground">Business activity today</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <KpiCard icon={CreditCard} label="Invoices" value={data.businessActivityToday.invoices} />
          <KpiCard icon={IndianRupee} label="Payments" value={data.businessActivityToday.payments} />
          <KpiCard icon={ShoppingCart} label="Purchases" value={data.businessActivityToday.purchases} />
        </div>
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted-foreground">Subscriptions</h2>
        <Card>
          <CardHeader><CardTitle className="text-base">Plan mix</CardTitle></CardHeader>
          <CardContent>
            <div className="grid grid-cols-3 gap-4 text-center">
              <div>
                <p className="tabular text-2xl font-semibold">{data.subscriptions.free}</p>
                <p className="text-xs text-muted-foreground">Free</p>
              </div>
              <div>
                <p className="tabular text-2xl font-semibold">{data.subscriptions.pro}</p>
                <p className="text-xs text-muted-foreground">Pro</p>
              </div>
              <div>
                <p className="tabular text-2xl font-semibold">{data.subscriptions.max}</p>
                <p className="text-xs text-muted-foreground">Max</p>
              </div>
            </div>
            <p className="mt-4 text-xs text-muted-foreground">
              Self-declared tiers - no billing gateway is connected yet, so this is plan mix,
              not verified paid revenue. See the Subscriptions &amp; Billing phase for real MRR.
            </p>
          </CardContent>
        </Card>
      </section>
    </>
  );
}

function KpiCard({
  icon: Icon, label, value, tone, trend, onClick,
}: {
  icon: typeof Building2;
  label: string;
  value: number;
  tone?: 'success' | 'danger';
  trend?: number | null;
  onClick?: () => void;
}) {
  return (
    <Card
      className={onClick ? 'cursor-pointer transition-colors hover:bg-muted/40' : undefined}
      onClick={onClick}
    >
      <CardContent className="flex items-center gap-3 p-4">
        <span className={
          'flex h-10 w-10 shrink-0 items-center justify-center rounded-md '
          + (tone === 'success' ? 'bg-success/10 text-success'
            : tone === 'danger' ? 'bg-destructive/10 text-destructive'
              : 'bg-primary/10 text-primary')
        }>
          <Icon className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <p className="tabular text-xl font-semibold leading-tight">{value}</p>
          <p className="truncate text-xs text-muted-foreground">{label}</p>
          {trend !== undefined && trend !== null ? (
            <p className={`mt-0.5 flex items-center gap-1 text-xs ${trend >= 0 ? 'text-success' : 'text-destructive'}`}>
              {trend >= 0 ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
              {Math.abs(trend).toFixed(1)}% vs last month
            </p>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
