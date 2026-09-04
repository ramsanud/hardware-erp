import { useCallback, useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Ban, Loader2, PlayCircle } from 'lucide-react';
import { BackLink } from '@/shared/components/BackLink';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { formatDateTime } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { platformAdminTenantService } from '../services/platformAdminTenantService';
import { platformAdminBillingService } from '../services/platformAdminBillingService';
import { TenantBackupCard } from '../components/TenantBackupCard';
import type { PlatformTenantDetailResponse, TenantBillingHistoryResponse } from '../types';
import { ApiError } from '@/shared/types/api';

const suspendSchema = z.object({
  reason: z.string().trim().min(1, 'A reason is required').max(500),
});
type SuspendValues = z.infer<typeof suspendSchema>;

const TIER_LABEL: Record<string, string> = { FREE: 'Free', PRO: 'Pro', MAX: 'Max' };
const WHATSAPP_LABEL: Record<string, string> = {
  CONNECTED: 'Connected', DISCONNECTED: 'Disconnected', NEEDS_ATTENTION: 'Needs attention',
};

function useTenantDetail(id: number) {
  const [tenant, setTenant] = useState<PlatformTenantDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTenant(await platformAdminTenantService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return { tenant, loading, error, reload };
}

export function PlatformAdminTenantDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const toast = useToast();
  const { admin } = usePlatformAdminAuth();
  const canManage = admin?.permissions.includes('TENANT_MANAGE') ?? false;
  const canViewBilling = admin?.permissions.includes('BILLING_VIEW') ?? false;
  const canViewBackups = admin?.permissions.includes('BACKUP_VIEW') ?? false;
  const canExportBackups = admin?.permissions.includes('BACKUP_MANAGE') ?? false;

  const [suspendOpen, setSuspendOpen] = useState(false);
  const [suspending, setSuspending] = useState(false);
  const [reactivateOpen, setReactivateOpen] = useState(false);
  const [billing, setBilling] = useState<TenantBillingHistoryResponse | null>(null);

  const { tenant, loading, error, reload } = useTenantDetail(id);

  useEffect(() => {
    if (!canViewBilling || Number.isNaN(id)) return;
    platformAdminBillingService.tenantHistory(id).then(setBilling).catch(() => setBilling(null));
  }, [id, canViewBilling]);

  const {
    register, handleSubmit, reset, formState: { errors },
  } = useForm<SuspendValues>({ resolver: zodResolver(suspendSchema), defaultValues: { reason: '' } });

  if (Number.isNaN(id)) return <Navigate to={PLATFORM_ADMIN_ROUTES.tenants} replace />;

  const submitSuspend = handleSubmit(async (values) => {
    setSuspending(true);
    try {
      await platformAdminTenantService.suspend(id, values.reason);
      toast.success(`${tenant?.name ?? 'Tenant'} suspended.`);
      reset();
      setSuspendOpen(false);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not suspend this tenant.');
    } finally {
      setSuspending(false);
    }
  });

  const handleReactivate = async () => {
    try {
      await platformAdminTenantService.reactivate(id);
      toast.success(`${tenant?.name ?? 'Tenant'} reactivated.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not reactivate this tenant.');
      throw caught;
    }
  };

  if (error) return <Card><ErrorState error={error} onRetry={reload} /></Card>;
  if (loading || !tenant) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <BackLink to={PLATFORM_ADMIN_ROUTES.tenants} label="Tenants" />

      <PageHeader
        title={tenant.name}
        description={`${tenant.slug} · Created ${formatDateTime(tenant.createdAt)}`}
        actions={
          canManage ? (
            tenant.status === 'ACTIVE' ? (
              <Button variant="outline" onClick={() => setSuspendOpen(true)}>
                <Ban className="h-4 w-4" />
                Suspend
              </Button>
            ) : (
              <Button onClick={() => setReactivateOpen(true)}>
                <PlayCircle className="h-4 w-4" />
                Reactivate
              </Button>
            )
          ) : null
        }
      />

      <div className="grid gap-5 lg:grid-cols-3">
        <Card>
          <CardHeader><CardTitle className="text-base">Overview</CardTitle></CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Row label="Owner" value={tenant.ownerName ?? 'No active owner'} />
            <Row label="Owner email" value={tenant.ownerEmail ?? '—'} />
            <Row label="Phone" value={tenant.phone ?? '—'} />
            <Row label="Email" value={tenant.email ?? '—'} />
            <Row label="Location" value={[tenant.city, tenant.stateCode].filter(Boolean).join(', ') || '—'} />
            <div className="flex items-center justify-between pt-1">
              <span className="text-muted-foreground">Status</span>
              <Badge variant={tenant.status === 'ACTIVE' ? 'default' : 'destructive'}>
                {tenant.status === 'ACTIVE' ? 'Active' : 'Suspended'}
              </Badge>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Last active</span>
              <span>{tenant.lastActiveAt ? formatDateTime(tenant.lastActiveAt) : 'Never'}</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Subscription</CardTitle></CardHeader>
          <CardContent className="space-y-2 text-sm">
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Plan</span>
              <Badge variant="outline">{TIER_LABEL[tenant.subscriptionTier]}</Badge>
            </div>
            {tenant.subscriptionTrialExpiresAt ? (
              <Row label="Trial expires" value={formatDateTime(tenant.subscriptionTrialExpiresAt)} />
            ) : null}
            {canViewBilling && billing && billing.payments.length > 0 ? (
              <div className="space-y-1.5 pt-2">
                <p className="text-xs font-medium text-muted-foreground">Payment history</p>
                {billing.payments.slice(0, 5).map((payment) => (
                  <div key={payment.paymentId} className="flex items-center justify-between text-xs">
                    <span>{TIER_LABEL[payment.requestedTier]} · ₹{(payment.amountPaise / 100).toLocaleString('en-IN')}</span>
                    <Badge variant={payment.status === 'CAPTURED' ? 'success' : 'destructive'} className="text-[10px]">
                      {payment.status}
                    </Badge>
                  </div>
                ))}
              </div>
            ) : (
              <p className="pt-2 text-xs text-muted-foreground">
                The plan may have been self-declared by the owner, or genuinely paid via checkout -
                see payment history above once billing is configured.
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Integrations</CardTitle></CardHeader>
          <CardContent className="space-y-2 text-sm">
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">WhatsApp</span>
              {tenant.whatsAppConnectionStatus ? (
                <Badge variant={tenant.whatsAppConnectionStatus === 'CONNECTED' ? 'default' : 'destructive'}>
                  {WHATSAPP_LABEL[tenant.whatsAppConnectionStatus]}
                </Badge>
              ) : (
                <span className="text-muted-foreground">Never connected</span>
              )}
            </div>
          </CardContent>
        </Card>

        {canViewBackups || canExportBackups ? (
          <TenantBackupCard tenantId={id} canView={canViewBackups} canExport={canExportBackups} />
        ) : null}

        <Card className="lg:col-span-3">
          <CardHeader><CardTitle className="text-base">Usage</CardTitle></CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-7">
              <UsageStat label="Users" value={tenant.usage.users} />
              <UsageStat label="Customers" value={tenant.usage.customers} />
              <UsageStat label="Products" value={tenant.usage.products} />
              <UsageStat label="Invoices" value={tenant.usage.invoices} />
              <UsageStat label="Purchases" value={tenant.usage.purchases} />
              <UsageStat label="Payments" value={tenant.usage.payments} />
              <UsageStat label="Expenses" value={tenant.usage.expenses} />
            </div>
          </CardContent>
        </Card>
      </div>

      <Dialog open={suspendOpen} onOpenChange={(open) => { if (!suspending) { setSuspendOpen(open); if (!open) reset(); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Suspend {tenant.name}</DialogTitle>
            <DialogDescription>
              This blocks every user of this shop from signing in, without touching their data.
              The reason is written to the platform audit log.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submitSuspend} className="space-y-4" noValidate>
            <FormField id="reason" label="Reason" error={errors.reason?.message} required>
              <textarea
                id="reason"
                rows={3}
                autoFocus
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                placeholder="e.g. Payment issue, abuse report, tenant's own request…"
                {...register('reason')}
              />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSuspendOpen(false)} disabled={suspending}>
                Cancel
              </Button>
              <Button type="submit" variant="destructive" loading={suspending}>Suspend tenant</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={reactivateOpen}
        onOpenChange={setReactivateOpen}
        title={`Reactivate ${tenant.name}?`}
        description="Every user of this shop will be able to sign in again immediately."
        confirmLabel="Reactivate"
        onConfirm={handleReactivate}
      />
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-muted-foreground">{label}</span>
      <span className="truncate text-right">{value}</span>
    </div>
  );
}

function UsageStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md border p-3 text-center">
      <p className="tabular text-xl font-semibold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  );
}
