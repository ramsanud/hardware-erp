import { useEffect, useState } from 'react';
import { Check, Loader2, Sparkles } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { Separator } from '@/shared/components/ui/separator';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { subscriptionService } from '../services/subscriptionService';
import type { CurrentSubscriptionResponse, SubscriptionPlanResponse } from '../types';

/**
 * CR-088 §21. The pricing/subscription page: three plans, feature
 * comparison, current plan + status + renewal, usage this month, and the
 * upgrade/downgrade/cancel actions. Prices and feature lists come from the
 * API (subscription_plan/plan_feature, V59) - nothing here is a hardcoded
 * price or feature list per plan.
 */
export function SubscriptionPage() {
  const toast = useToast();
  const [plans, setPlans] = useState<SubscriptionPlanResponse[] | null>(null);
  const [current, setCurrent] = useState<CurrentSubscriptionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [changingTo, setChangingTo] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([subscriptionService.plans(), subscriptionService.current()])
      .then(([plansResult, currentResult]) => {
        setPlans(plansResult);
        setCurrent(currentResult);
      })
      .catch((caught) => setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 })))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const changePlan = async (planCode: string) => {
    setChangingTo(planCode);
    try {
      const updated = await subscriptionService.upgrade(planCode);
      setCurrent(updated);
      toast.success(`Your shop is now on the ${updated.planName} plan.`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'UPGRADE_REQUIRES_CHECKOUT') {
        toast.error(caught, 'Upgrading to a paid plan needs checkout - use Billing in Shop Settings.');
      } else {
        toast.error(caught, 'Could not change the plan. Please try again.');
      }
    } finally {
      setChangingTo(null);
    }
  };

  const cancel = async () => {
    setCancelling(true);
    try {
      const updated = await subscriptionService.cancel('Cancelled from the plans page');
      setCurrent(updated);
      toast.success('Subscription cancelled - your data is kept and Basic features stay available.');
    } catch (caught) {
      toast.error(caught, 'Could not cancel the subscription.');
    } finally {
      setCancelling(false);
    }
  };

  if (error) {
    return (
      <div className="mx-auto max-w-5xl space-y-6">
        <PageHeader title="Subscription plan" />
        <Card><ErrorState error={error} onRetry={load} /></Card>
      </div>
    );
  }

  if (loading || !plans || !current) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  const sortedPlans = [...plans].sort((a, b) => a.displayOrder - b.displayOrder);

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Subscription plan"
        description="Basic covers daily shop operations. Pro adds automation and advanced reports. Premium adds intelligence, growth and multi-branch."
      />

      <CurrentPlanCard current={current} onCancel={cancel} cancelling={cancelling} />

      <div className="grid gap-4 sm:grid-cols-3">
        {sortedPlans.map((plan) => (
          <PlanCard
            key={plan.planCode}
            plan={plan}
            isCurrent={plan.planCode === current.planCode}
            changing={changingTo === plan.planCode}
            disabled={changingTo !== null}
            onSelect={() => changePlan(plan.planCode)}
          />
        ))}
      </div>

      <UsageCard current={current} />
    </div>
  );
}

function CurrentPlanCard({ current, onCancel, cancelling }: {
  current: CurrentSubscriptionResponse;
  onCancel: () => void;
  cancelling: boolean;
}) {
  const statusTone: Record<string, string> = {
    TRIAL: 'bg-primary/10 text-primary',
    ACTIVE: 'bg-success/10 text-success',
    PAST_DUE: 'bg-warning/10 text-warning',
    EXPIRED: 'bg-destructive/10 text-destructive',
    CANCELLED: 'bg-muted text-muted-foreground',
    SUSPENDED: 'bg-destructive/10 text-destructive',
  };
  const canCancel = current.status !== 'CANCELLED' && current.tier !== 'FREE';

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardDescription>Current plan</CardDescription>
          <CardTitle className="flex items-center gap-2 text-xl">
            {current.planName}
            <Badge className={cn('font-normal', statusTone[current.status] ?? 'bg-muted text-muted-foreground')}>
              {current.status.replace('_', ' ')}
            </Badge>
          </CardTitle>
          {current.effectivePlanCode !== current.planCode ? (
            <p className="mt-1 text-sm text-warning">
              Your subscription is {current.status.toLowerCase().replace('_', ' ')} - only {current.effectivePlanName} features
              are available until it is renewed. Your data is kept.
            </p>
          ) : null}
          {current.status === 'TRIAL' && current.trialEndsAt ? (
            <p className="mt-1 text-sm text-muted-foreground">
              Trial ends {new Date(current.trialEndsAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })}
            </p>
          ) : null}
          {current.renewalAt ? (
            <p className="mt-1 text-sm text-muted-foreground">
              Renews {new Date(current.renewalAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })}
            </p>
          ) : null}
        </div>
        {canCancel ? (
          <Button variant="outline" size="sm" onClick={onCancel} disabled={cancelling}>
            {cancelling ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Cancel subscription
          </Button>
        ) : null}
      </CardHeader>
    </Card>
  );
}

function PlanCard({ plan, isCurrent, changing, disabled, onSelect }: {
  plan: SubscriptionPlanResponse;
  isCurrent: boolean;
  changing: boolean;
  disabled: boolean;
  onSelect: () => void;
}) {
  return (
    <Card className={cn('relative flex flex-col', plan.recommended && !isCurrent && 'border-primary shadow-sm')}>
      {plan.recommended ? (
        <Badge className="absolute -top-2.5 right-4 gap-1 bg-primary text-primary-foreground">
          <Sparkles className="h-3 w-3" /> Recommended
        </Badge>
      ) : null}
      <CardHeader>
        <CardTitle>{plan.planName}</CardTitle>
        <CardDescription>{plan.tagline}</CardDescription>
        <p className="pt-2 text-3xl font-semibold tracking-tight">
          {plan.priceDisplay}
          <span className="text-sm font-normal text-muted-foreground">/{plan.billingPeriod === 'YEARLY' ? 'year' : 'month'}</span>
        </p>
      </CardHeader>
      <CardContent className="flex-1 space-y-2">
        {plan.usageLimits.map((limit) => (
          <div key={limit.usageKey} className="flex items-start gap-2 text-sm">
            <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" />
            <span>{limit.includedCount.toLocaleString('en-IN')} {limit.label.toLowerCase()}/month included</span>
          </div>
        ))}
        <Separator className="my-2" />
        <p className="text-sm text-muted-foreground">{plan.featureKeys.length} features included</p>
      </CardContent>
      <CardFooter>
        {isCurrent ? (
          <Button className="w-full" variant="secondary" disabled>Current plan</Button>
        ) : (
          <Button className="w-full" variant={plan.recommended ? 'default' : 'outline'} onClick={onSelect} disabled={disabled}>
            {changing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Switch to {plan.planName}
          </Button>
        )}
      </CardFooter>
    </Card>
  );
}

function UsageCard({ current }: { current: CurrentSubscriptionResponse }) {
  if (current.usage.items.length === 0) {
    return null;
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Usage this month</CardTitle>
        <CardDescription>Metered channels - WhatsApp, SMS, email and AI cost real money per message, so each plan includes a fixed monthly amount.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {current.usage.items.map((item) => {
          const percent = item.includedCount <= 0 ? 0 : Math.min(100, Math.round((item.usedCount / item.includedCount) * 100));
          const nearLimit = item.includedCount > 0 && item.usedCount / item.includedCount >= 0.8;
          return (
            <div key={item.usageKey}>
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="text-muted-foreground">{item.label}</span>
                <span className={cn('tabular font-medium', item.limitReached && 'text-destructive', !item.limitReached && nearLimit && 'text-warning')}>
                  {item.usedCount.toLocaleString('en-IN')} / {item.includedCount.toLocaleString('en-IN')}
                </span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted" role="progressbar"
                   aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} aria-label={`${item.label} usage`}>
                <div
                  className={cn('h-full rounded-full', item.limitReached ? 'bg-destructive' : nearLimit ? 'bg-warning' : 'bg-primary')}
                  style={{ width: `${percent}%` }}
                />
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
