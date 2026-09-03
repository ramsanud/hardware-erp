import { useEffect, useState } from 'react';
import { CreditCard, Loader2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/components/ui/card';
import { EmptyState } from '@/shared/components/EmptyState';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { billingService } from '../services/billingService';
import { loadRazorpayCheckout } from '../utils/loadRazorpayCheckout';
import { SUBSCRIPTION_TIERS } from '../constants/subscriptionTiers';
import type { SubscriptionTier, TenantBillingHistoryResponse } from '../types';

interface BillingUpgradeCardProps {
  currentTier: SubscriptionTier;
  tenantName: string;
  ownerEmail?: string | null;
  ownerPhone?: string | null;
  /** So the caller can patch its own subscriptionTier state the moment a payment is verified - matches SubscriptionCouponsCard's own onRedeemed convention. */
  onUpgraded?: (tier: SubscriptionTier) => void;
}

const UPGRADE_TARGETS: SubscriptionTier[] = ['PRO', 'MAX'];

/**
 * CR-057 phase 9 - real Razorpay Checkout.js flow: create an order server-
 * side, open the widget, verify the signed callback server-side. Shows an
 * honest "Billing is not configured" state rather than a broken button when
 * this environment has no Razorpay credentials (spec's own "no fake data").
 */
export function BillingUpgradeCard({ currentTier, tenantName, ownerEmail, ownerPhone, onUpgraded }: BillingUpgradeCardProps) {
  const toast = useToast();
  const [history, setHistory] = useState<TenantBillingHistoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [notConfigured, setNotConfigured] = useState(false);
  const [payingTier, setPayingTier] = useState<SubscriptionTier | null>(null);

  useEffect(() => {
    billingService.history()
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoading(false));
  }, []);

  const startCheckout = async (tier: SubscriptionTier) => {
    setPayingTier(tier);
    setNotConfigured(false);
    try {
      const order = await billingService.checkout(tier);
      await loadRazorpayCheckout();

      const razorpay = new window.Razorpay!({
        key: order.razorpayKeyId,
        order_id: order.razorpayOrderId,
        amount: order.amountPaise,
        currency: order.currency,
        name: tenantName,
        description: `Upgrade to ${SUBSCRIPTION_TIERS[tier].label}`,
        prefill: { email: ownerEmail ?? undefined, contact: ownerPhone ?? undefined },
        handler: async (response: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) => {
          try {
            await billingService.verify({
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            toast.success(`You're now on ${SUBSCRIPTION_TIERS[tier].label}.`);
            onUpgraded?.(tier);
            billingService.history().then(setHistory).catch(() => {});
          } catch (caught) {
            toast.error(caught, 'Payment could not be verified. If money was deducted, it will be reconciled automatically.');
          }
        },
        modal: { ondismiss: () => setPayingTier(null) },
      });
      razorpay.open();
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'BILLING_NOT_CONFIGURED') {
        setNotConfigured(true);
      } else {
        toast.error(caught, 'Could not start checkout.');
      }
    } finally {
      setPayingTier(null);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <CreditCard className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Upgrade plan</CardTitle>
        </div>
        <CardDescription>
          You are currently on <span className="font-medium">{SUBSCRIPTION_TIERS[currentTier].label}</span>. Upgrade for
          higher usage limits and more features.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {notConfigured ? (
          <Alert variant="warning">
            <AlertDescription>
              Billing is not configured for this environment yet. Contact support to upgrade your plan.
            </AlertDescription>
          </Alert>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          {UPGRADE_TARGETS.filter((tier) => tier !== currentTier).map((tier) => (
            <div key={tier} className="rounded-md border p-4">
              <div className="font-medium">{SUBSCRIPTION_TIERS[tier].label}</div>
              <ul className="mt-1 space-y-0.5 text-sm text-muted-foreground">
                {SUBSCRIPTION_TIERS[tier].features.map((feature) => <li key={feature}>{feature}</li>)}
              </ul>
              <Button type="button" size="sm" className="mt-3" loading={payingTier === tier}
                      onClick={() => startCheckout(tier)}>
                Upgrade to {SUBSCRIPTION_TIERS[tier].label}
              </Button>
            </div>
          ))}
        </div>

        <div>
          <div className="mb-2 text-sm font-medium">Payment history</div>
          {loading ? (
            <div className="flex justify-center py-4">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading" />
            </div>
          ) : !history || history.payments.length === 0 ? (
            <EmptyState icon={CreditCard} title="No payments yet" description="Your plan upgrade payments will appear here." />
          ) : (
            <ul className="space-y-2">
              {history.payments.map((payment) => (
                <li key={payment.paymentId} className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                  <span>
                    {SUBSCRIPTION_TIERS[payment.requestedTier].label} - Rs {(payment.amountPaise / 100).toLocaleString('en-IN')}
                  </span>
                  <Badge variant={payment.status === 'CAPTURED' ? 'success' : 'destructive'}>{payment.status}</Badge>
                </li>
              ))}
            </ul>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
