import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Gift, Loader2, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Badge } from '@/shared/components/ui/badge';
import {
  Card, CardContent, CardHeader, CardTitle, CardDescription,
} from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/shared/components/ui/dialog';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { EmptyState } from '@/shared/components/EmptyState';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { subscriptionCouponService } from '../services/subscriptionCouponService';
import { SUBSCRIPTION_TIERS } from '../constants/subscriptionTiers';
import type { SubscriptionCouponRedemptionResponse, SubscriptionCouponResponse, SubscriptionTier } from '../types';

const couponSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(30),
  description: z.string().trim().max(255).optional().or(z.literal('')),
  grantedTier: z.enum(['FREE', 'PRO', 'MAX']),
  trialDays: z.coerce.number().int().positive('Must be at least 1 day'),
  usageLimit: z.coerce.number().int().positive().optional(),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});
type CouponFormValues = z.infer<typeof couponSchema>;

/**
 * CR-032 - "give a complete free coupon" (a promotional trial code the
 * OWNER creates for their own tenant, redeemed to grant a plan tier free
 * for a set number of days, then it auto-reverts to FREE). Lives in Shop
 * Settings next to the plan picker and usage dashboard rather than as its
 * own routed module - a low-traffic, OWNER-only admin feature, not worth
 * a full list/detail page pair the way the retail Coupon module has.
 */
interface SubscriptionCouponsCardProps {
  /**
   * Called with the redemption result so the caller can patch its own
   * subscriptionTier/trialExpiresAt state directly. Deliberately not a
   * "just refetch everything" callback: ShopSettingsPage's reload() flips
   * a page-wide loading flag that unmounts this whole card while it runs,
   * which would silently wipe redeemResult below before anyone sees it -
   * a real bug caught by testing this live, not a hypothetical.
   */
  onRedeemed?: (result: SubscriptionCouponRedemptionResponse) => void;
}

export function SubscriptionCouponsCard({ onRedeemed }: SubscriptionCouponsCardProps) {
  const toast = useToast();
  const [coupons, setCoupons] = useState<SubscriptionCouponResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [formOpen, setFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [redeemCode, setRedeemCode] = useState('');
  const [redeeming, setRedeeming] = useState(false);
  const [redeemResult, setRedeemResult] = useState<string | null>(null);

  const {
    register, control, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm<CouponFormValues>({
    resolver: zodResolver(couponSchema),
    defaultValues: { code: '', description: '', grantedTier: 'MAX', trialDays: 30, status: 'ACTIVE' },
  });

  const reload = () => {
    setLoading(true);
    subscriptionCouponService.search({ size: 50 })
      .then((page) => setCoupons(page.content))
      .catch(() => setCoupons([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { reload(); }, []);

  const openCreate = () => {
    reset({ code: '', description: '', grantedTier: 'MAX', trialDays: 30, status: 'ACTIVE' });
    setFormError(null);
    setFormOpen(true);
  };

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await subscriptionCouponService.create({
        code: values.code,
        description: values.description || null,
        grantedTier: values.grantedTier as SubscriptionTier,
        trialDays: values.trialDays,
        usageLimit: values.usageLimit ?? null,
        status: values.status,
      });
      setFormOpen(false);
      toast.success(`Coupon "${values.code.toUpperCase()}" created.`);
      reload();
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFormError(caught.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  const handleDelete = async (coupon: SubscriptionCouponResponse) => {
    try {
      await subscriptionCouponService.remove(coupon.id);
      toast.success(`Coupon "${coupon.code}" removed.`);
      reload();
    } catch (caught) {
      toast.error(caught, 'Could not remove this coupon.');
    }
  };

  const handleRedeem = async () => {
    if (!redeemCode.trim()) return;
    setRedeeming(true);
    setRedeemResult(null);
    try {
      const result = await subscriptionCouponService.redeem(redeemCode.trim());
      const expiryDate = new Date(result.trialExpiresAt).toLocaleDateString('en-IN', {
        day: 'numeric', month: 'short', year: 'numeric',
      });
      setRedeemResult(`You're now on ${SUBSCRIPTION_TIERS[result.grantedTier].label} until ${expiryDate}.`);
      setRedeemCode('');
      toast.success('Coupon redeemed.');
      onRedeemed?.(result);
      reload();
    } catch (caught) {
      toast.error(caught, 'Could not redeem this coupon.');
    } finally {
      setRedeeming(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Gift className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Subscription coupons</CardTitle>
        </div>
        <CardDescription>
          Create a code that grants a plan free for a set number of days - it reverts to Free automatically once that period ends.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <Input
            value={redeemCode}
            onChange={(event) => setRedeemCode(event.target.value.toUpperCase())}
            placeholder="Have a code? Enter it here"
            className="uppercase sm:max-w-xs"
          />
          <Button type="button" variant="outline" onClick={handleRedeem} loading={redeeming} disabled={!redeemCode.trim()}>
            Redeem
          </Button>
        </div>
        {redeemResult ? (
          <Alert variant="success"><AlertDescription>{redeemResult}</AlertDescription></Alert>
        ) : null}

        {loading ? (
          <div className="flex justify-center py-6">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading" />
          </div>
        ) : coupons.length === 0 ? (
          <EmptyState icon={Gift} title="No subscription coupons yet"
                      description="Create one to give a shop (including your own) a plan free for a while." />
        ) : (
          <div className="overflow-x-auto rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Grants</TableHead>
                  <TableHead>Trial</TableHead>
                  <TableHead>Used</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {coupons.map((coupon) => (
                  <TableRow key={coupon.id}>
                    <TableCell className="font-mono text-sm">{coupon.code}</TableCell>
                    <TableCell>{SUBSCRIPTION_TIERS[coupon.grantedTier].label}</TableCell>
                    <TableCell>{coupon.trialDays} days</TableCell>
                    <TableCell className="tabular">{coupon.timesUsed}{coupon.usageLimit ? ` / ${coupon.usageLimit}` : ''}</TableCell>
                    <TableCell>
                      <Badge variant={coupon.status === 'ACTIVE' ? 'success' : 'secondary'}>{coupon.status}</Badge>
                    </TableCell>
                    <TableCell>
                      <Button type="button" variant="ghost" size="icon" className="h-8 w-8"
                              aria-label={`Delete coupon ${coupon.code}`} onClick={() => handleDelete(coupon)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}

        <Button type="button" variant="outline" onClick={openCreate}>
          <Plus className="h-4 w-4" />
          Add coupon
        </Button>
      </CardContent>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Add subscription coupon</DialogTitle></DialogHeader>
          <form onSubmit={submit} noValidate className="space-y-4">
            {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}
            <FormField id="code" label="Code" error={errors.code?.message} required>
              <Input id="code" className="uppercase" placeholder="WELCOME2026"
                     aria-invalid={Boolean(errors.code)} {...register('code')} />
            </FormField>
            <FormField id="description" label="Description" error={errors.description?.message}>
              <Input id="description" placeholder="100% free for a month" {...register('description')} />
            </FormField>
            <div className="grid grid-cols-2 gap-4">
              <FormField id="grantedTier" label="Grants plan" error={errors.grantedTier?.message} required>
                <Controller
                  control={control}
                  name="grantedTier"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="grantedTier"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="FREE">Free</SelectItem>
                        <SelectItem value="PRO">Pro</SelectItem>
                        <SelectItem value="MAX">Max</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </FormField>
              <FormField id="trialDays" label="Trial days" error={errors.trialDays?.message} required>
                <Controller
                  control={control}
                  name="trialDays"
                  render={({ field }) => (
                    <NumberInput id="trialDays" min={1} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
                  )}
                />
              </FormField>
            </div>
            <FormField id="usageLimit" label="Usage limit (optional)" error={errors.usageLimit?.message}
                       hint="Leave blank for unlimited redemptions.">
              <Controller
                control={control}
                name="usageLimit"
                render={({ field }) => (
                  <NumberInput id="usageLimit" min={0} value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
                )}
              />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormOpen(false)} disabled={isSubmitting}>Cancel</Button>
              <Button type="submit" loading={isSubmitting}>Create coupon</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
