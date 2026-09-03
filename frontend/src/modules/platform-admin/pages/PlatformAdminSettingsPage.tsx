import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CreditCard, Loader2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Badge } from '@/shared/components/ui/badge';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { FormField } from '@/shared/components/FormField';
import { formatDateTime } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { platformAdminSettingsService } from '../services/platformAdminSettingsService';
import type { RazorpayConfigResponse } from '../types';

const schema = z.object({
  enabled: z.boolean(),
  keyId: z.string().trim().max(100).optional().or(z.literal('')),
  keySecret: z.string().trim().max(500).optional().or(z.literal('')),
  webhookSecret: z.string().trim().max(500).optional().or(z.literal('')),
  proPlanAmountPaise: z.coerce.number().int().positive(),
  maxPlanAmountPaise: z.coerce.number().int().positive(),
});
type FormValues = z.infer<typeof schema>;

const SOURCE_LABEL: Record<string, string> = {
  DATABASE: 'This console’s own saved credentials',
  ENVIRONMENT: 'Deployment environment variables (RAZORPAY_*)',
  NOT_CONFIGURED: 'Not configured anywhere',
};

/**
 * CR-057 phase 12 - Platform Settings. Fill in real Razorpay credentials
 * here instead of redeploying with new environment variables. Secrets are
 * write-only: the GET response never carries a saved secret back, only
 * whether one is set - the password fields below always start blank, and
 * leaving one blank on save means "keep what's already there", not "clear it".
 */
export function PlatformAdminSettingsPage() {
  const toast = useToast();
  const { admin } = usePlatformAdminAuth();
  const canManage = admin?.permissions.includes('BILLING_MANAGE') ?? false;

  const [config, setConfig] = useState<RazorpayConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const {
    register, handleSubmit, reset, watch, setValue, formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { enabled: false, keyId: '', keySecret: '', webhookSecret: '', proPlanAmountPaise: 99900, maxPlanAmountPaise: 299900 },
  });

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await platformAdminSettingsService.getRazorpayConfig();
      setConfig(result);
      reset({
        enabled: result.enabled,
        keyId: result.keyId ?? '',
        keySecret: '',
        webhookSecret: '',
        proPlanAmountPaise: result.proPlanAmountPaise,
        maxPlanAmountPaise: result.maxPlanAmountPaise,
      });
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const submit = handleSubmit(async (values) => {
    try {
      const updated = await platformAdminSettingsService.updateRazorpayConfig({
        enabled: values.enabled,
        keyId: values.keyId || null,
        // Blank means "leave unchanged" here, matching the backend's own null-means-unchanged contract -
        // undefined is sent (not an empty string) so a field the admin never touched is never accidentally cleared.
        keySecret: values.keySecret ? values.keySecret : undefined,
        webhookSecret: values.webhookSecret ? values.webhookSecret : undefined,
        proPlanAmountPaise: values.proPlanAmountPaise,
        maxPlanAmountPaise: values.maxPlanAmountPaise,
      });
      setConfig(updated);
      reset({
        enabled: updated.enabled, keyId: updated.keyId ?? '', keySecret: '', webhookSecret: '',
        proPlanAmountPaise: updated.proPlanAmountPaise, maxPlanAmountPaise: updated.maxPlanAmountPaise,
      });
      toast.success('Razorpay settings saved.');
    } catch (caught) {
      toast.error(caught, 'Could not save Razorpay settings.');
    }
  });

  if (error) return <Card><ErrorState error={error} onRetry={load} /></Card>;
  if (loading || !config) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <PageHeader title="Platform Settings" description="Console-wide configuration for the whole platform, not any one tenant." />

      <div className="max-w-2xl">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <CreditCard className="h-4 w-4 text-primary" aria-hidden />
              <CardTitle className="text-base">Razorpay billing</CardTitle>
              <Badge variant={config.source === 'NOT_CONFIGURED' ? 'destructive' : config.source === 'DATABASE' ? 'success' : 'secondary'}>
                {SOURCE_LABEL[config.source]}
              </Badge>
            </div>
            <CardDescription>
              Lets tenants upgrade their plan through real checkout. Saved here, this overrides the
              RAZORPAY_* environment variables set at deploy time; leave disabled to fall back to them.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit} noValidate className="space-y-4">
              {!canManage ? (
                <Alert variant="warning"><AlertDescription>You can view this but not save changes.</AlertDescription></Alert>
              ) : null}

              <div className="flex items-start gap-3 rounded-md border px-3 py-2.5">
                <Checkbox id="enabled" checked={watch('enabled')} disabled={!canManage}
                          onCheckedChange={(checked) => setValue('enabled', checked === true)} className="mt-0.5" />
                <label htmlFor="enabled" className="flex-1 cursor-pointer text-sm">
                  <span className="font-medium">Enabled</span>
                  <p className="text-muted-foreground">Off means tenants see &quot;billing not configured&quot;, even if keys are filled in below.</p>
                </label>
              </div>

              <FormField id="keyId" label="Key ID" error={errors.keyId?.message} hint="Public - safe to see, e.g. rzp_live_xxxxxxxx">
                <Input id="keyId" placeholder="rzp_live_..." disabled={!canManage} {...register('keyId')} />
              </FormField>

              <FormField id="keySecret" label="Key secret" error={errors.keySecret?.message}
                         hint={config.keySecretConfigured ? 'A secret is already saved - leave blank to keep it.' : 'Not set yet.'}>
                <Input id="keySecret" type="password" placeholder={config.keySecretConfigured ? '••••••••••••' : 'Enter to set'}
                       disabled={!canManage} autoComplete="new-password" {...register('keySecret')} />
              </FormField>

              <FormField id="webhookSecret" label="Webhook secret" error={errors.webhookSecret?.message}
                         hint={config.webhookSecretConfigured ? 'Already saved - leave blank to keep it.' : 'From Razorpay Dashboard → Settings → Webhooks.'}>
                <Input id="webhookSecret" type="password" placeholder={config.webhookSecretConfigured ? '••••••••••••' : 'Enter to set'}
                       disabled={!canManage} autoComplete="new-password" {...register('webhookSecret')} />
              </FormField>

              <div className="grid grid-cols-2 gap-4">
                <FormField id="proPlanAmountPaise" label="Pro plan price (paise)" error={errors.proPlanAmountPaise?.message}>
                  <Input id="proPlanAmountPaise" type="number" inputMode="numeric" disabled={!canManage}
                         {...register('proPlanAmountPaise')} />
                </FormField>
                <FormField id="maxPlanAmountPaise" label="Max plan price (paise)" error={errors.maxPlanAmountPaise?.message}>
                  <Input id="maxPlanAmountPaise" type="number" inputMode="numeric" disabled={!canManage}
                         {...register('maxPlanAmountPaise')} />
                </FormField>
              </div>

              {config.updatedAt ? (
                <p className="text-xs text-muted-foreground">Last saved {formatDateTime(config.updatedAt)}.</p>
              ) : null}

              {canManage ? (
                <Button type="submit" loading={isSubmitting}>Save</Button>
              ) : null}
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
