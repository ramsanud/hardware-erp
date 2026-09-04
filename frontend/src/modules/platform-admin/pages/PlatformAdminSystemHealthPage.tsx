import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle, CheckCircle2, CircleHelp, Database, Loader2, Mail,
  MessageCircle, Server, ShieldCheck, Timer, XCircle,
} from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { formatDateTime } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { platformAdminSystemHealthService } from '../services/platformAdminSystemHealthService';
import type { HealthStatus, PlatformServiceName, ServiceHealth, SystemHealthResponse } from '../types';

const SERVICE_ICON: Record<PlatformServiceName, typeof Server> = {
  BACKEND: Server,
  DATABASE: Database,
  AUTHENTICATION: ShieldCheck,
  STORAGE: Database,
  WHATSAPP: MessageCircle,
  EMAIL: Mail,
  BACKGROUND_JOBS: Timer,
};

const SERVICE_LABEL: Record<PlatformServiceName, string> = {
  BACKEND: 'Backend',
  DATABASE: 'Database',
  AUTHENTICATION: 'Authentication',
  STORAGE: 'Storage',
  WHATSAPP: 'WhatsApp',
  EMAIL: 'Email',
  BACKGROUND_JOBS: 'Background jobs',
};

const STATUS_CONFIG: Record<HealthStatus, { label: string; badge: 'default' | 'destructive' | 'secondary' | 'outline'; icon: typeof CheckCircle2 }> = {
  HEALTHY: { label: 'Healthy', badge: 'default', icon: CheckCircle2 },
  DEGRADED: { label: 'Degraded', badge: 'secondary', icon: AlertCircle },
  DOWN: { label: 'Down', badge: 'destructive', icon: XCircle },
  UNKNOWN: { label: 'Unknown', badge: 'outline', icon: CircleHelp },
};

export function PlatformAdminSystemHealthPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<SystemHealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await platformAdminSystemHealthService.overview());
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
        title="System Health"
        description={`Live checks, computed fresh on every load · ${formatDateTime(data.generatedAt)}`}
        actions={
          <button
            type="button"
            className="text-sm text-primary underline-offset-4 hover:underline"
            onClick={() => navigate(PLATFORM_ADMIN_ROUTES.incidents)}
          >
            View incidents
          </button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {data.services.map((service) => <ServiceCard key={service.service} health={service} />)}
      </div>
    </>
  );
}

function ServiceCard({ health }: { health: ServiceHealth }) {
  const Icon = SERVICE_ICON[health.service];
  const config = STATUS_CONFIG[health.status];
  const StatusIcon = config.icon;

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <Icon className="h-4 w-4 text-muted-foreground" />
          {SERVICE_LABEL[health.service]}
        </CardTitle>
        <Badge variant={config.badge} className="gap-1">
          <StatusIcon className="h-3 w-3" />
          {config.label}
        </Badge>
      </CardHeader>
      <CardContent className="space-y-1.5 text-xs text-muted-foreground">
        {health.responseTimeMs !== null ? (
          <div className="flex justify-between"><span>Response time</span><span className="tabular">{health.responseTimeMs} ms</span></div>
        ) : null}
        <div className="flex justify-between">
          <span>Last checked</span>
          <span>{health.lastCheckedAt ? formatDateTime(health.lastCheckedAt) : 'Not yet'}</span>
        </div>
        <div className="flex justify-between">
          <span>Last failure</span>
          <span>{health.lastFailureAt ? formatDateTime(health.lastFailureAt) : 'None'}</span>
        </div>
        <div className="flex justify-between">
          <span>Errors (24h)</span>
          <span className="tabular">{health.errorCount}</span>
        </div>
        {health.detail ? <p className="pt-1.5 text-[11px] leading-snug">{health.detail}</p> : null}
      </CardContent>
    </Card>
  );
}
