import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Check } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Card, CardContent, CardHeader, CardTitle, CardDescription,
} from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { ApiError } from '@/shared/types/api';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { AUTH_ROUTES } from '@/modules/auth/constants';
import { SUBSCRIPTION_TIER_OPTIONS, SUBSCRIPTION_TIERS } from '@/modules/settings/constants/subscriptionTiers';
import type { SubscriptionTier } from '@/modules/settings/types';
import { tenantRegistrationService } from '../services/tenantRegistrationService';
import {
  ConsentSection, EMPTY_CONSENT, canAcceptTerms, type ConsentState,
} from '../components/ConsentSection';
import { PRIVACY_VERSION, TERMS_VERSION } from '../components/LegalContent';

const registerSchema = z.object({
  shopName: z.string().trim().min(1, 'Shop name is required').max(200),
  ownerFullName: z.string().trim().min(1, 'Your name is required').max(200),
  mobileNo: z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number'),
  email: z.string().trim().email('Enter a valid email address'),
  password: z.string().min(8, 'At least 8 characters').max(72)
    .regex(/^(?=.*[A-Za-z])(?=.*\d).+$/, 'Add at least one letter and one number'),
  subscriptionTier: z.enum(['FREE', 'PRO', 'MAX']),
});
// Consent is held outside the schema because part of it - whether each
// document has been opened - is interaction state rather than a form field.
// It is gated explicitly in submit() below, and again on the server.
type RegisterValues = z.infer<typeof registerSchema>;

export function RegisterPage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register, control, handleSubmit, watch, formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      shopName: '', ownerFullName: '', mobileNo: '', email: '', password: '', subscriptionTier: 'FREE',
    },
  });

  const [consent, setConsent] = useState<ConsentState>(EMPTY_CONSENT);
  const [consentError, setConsentError] = useState<string | null>(null);

  const shopName = watch('shopName');
  const debouncedShopName = useDebouncedValue(shopName, SEARCH_DEBOUNCE_MS);
  const [slugStatus, setSlugStatus] = useState<'idle' | 'checking' | 'available' | 'taken'>('idle');

  useEffect(() => {
    if (!debouncedShopName.trim()) { setSlugStatus('idle'); return; }
    let cancelled = false;
    setSlugStatus('checking');
    tenantRegistrationService.slugAvailable(debouncedShopName)
      .then((result) => { if (!cancelled) setSlugStatus(result.available ? 'available' : 'taken'); })
      .catch(() => { if (!cancelled) setSlugStatus('idle'); });
    return () => { cancelled = true; };
  }, [debouncedShopName]);

  const submit = handleSubmit(async (values) => {
    setFormError(null);

    // Checked here as well as by the disabled button, so submitting by any
    // other route - Enter in a field, a stale render - is still blocked. The
    // server rejects it a third time; none of these three is load-bearing
    // alone.
    if (!canAcceptTerms(consent)) {
      setConsentError('Please review the required documents before continuing.');
      return;
    }
    if (!consent.termsAccepted) {
      setConsentError('Please agree to the Terms & Conditions before creating your account.');
      return;
    }
    setConsentError(null);

    try {
      await tenantRegistrationService.register({
        ...values,
        termsAccepted: true,
        // The versions actually rendered to this user. The server rejects
        // anything that is not the current published version.
        termsVersion: TERMS_VERSION,
        privacyVersion: PRIVACY_VERSION,
        marketingConsent: consent.marketingConsent,
      });
      navigate(AUTH_ROUTES.login, {
        replace: true,
        state: { registered: true },
      });
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFormError(caught.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    // Wider than the previous max-w-lg: the consent section needs room for
    // document rows without them wrapping into an unreadable stack.
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>Create your account</CardTitle>
        <CardDescription>Register a new hardware shop - one shop, one owner login.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {formError ? (
          <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
        ) : null}

        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormField id="shopName" label="Shop name" error={errors.shopName?.message} required
                     hint={slugStatus === 'checking' ? 'Checking availability…'
                       : slugStatus === 'available' ? 'Available'
                       : slugStatus === 'taken' ? 'A shop with a very similar name already exists' : undefined}>
            <Input id="shopName" autoFocus {...register('shopName')} />
          </FormField>

          <FormField id="ownerFullName" label="Your name" error={errors.ownerFullName?.message} required>
            <Input id="ownerFullName" {...register('ownerFullName')} />
          </FormField>

          {/* Mobile and email used to share a 2-column grid inside a max-w-lg
              card, leaving the email input around 230px - too narrow to read
              back a normal address without it scrolling. Both are full width
              now, matching every other field on this form. */}
          <FormField id="mobileNo" label="Mobile number" error={errors.mobileNo?.message} required>
            <Input id="mobileNo" inputMode="tel" placeholder="9876543210" {...register('mobileNo')} />
          </FormField>

          <FormField id="email" label="Email address" error={errors.email?.message} required>
            <Input id="email" type="email" inputMode="email" autoComplete="email"
                   placeholder="you@yourshop.com" {...register('email')} />
          </FormField>

          <FormField id="password" label="Password" error={errors.password?.message} required
                     hint="At least 8 characters, with a letter and a number.">
            <Input id="password" type="password" {...register('password')} />
          </FormField>

          <FormField id="subscriptionTier" label="Plan" error={errors.subscriptionTier?.message} required>
            <Controller
              control={control}
              name="subscriptionTier"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="subscriptionTier"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {SUBSCRIPTION_TIER_OPTIONS.map((tier) => (
                      <SelectItem key={tier} value={tier}>{SUBSCRIPTION_TIERS[tier].label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
          <PlanFeatures tier={watch('subscriptionTier')} />
          <p className="text-xs text-muted-foreground">
            No payment is collected - this only turns on features for your shop. Change it anytime from Shop Settings.
          </p>

          <div className="h-px bg-border" role="separator" />

          <ConsentSection
            value={consent}
            onChange={(next) => { setConsent(next); if (consentError) setConsentError(null); }}
            error={consentError ?? undefined}
          />

          {/* Disabled is only the visible signal - submit() re-checks, and so
              does the server. */}
          <Button type="submit" className="w-full" loading={isSubmitting}
                  disabled={!consent.termsAccepted}>
            Create account
          </Button>
        </form>

        <div className="text-center text-sm">
          Already have a shop?{' '}
          <Link to={AUTH_ROUTES.login} className="text-primary underline-offset-4 hover:underline">
            Sign in
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}

function PlanFeatures({ tier }: { tier: SubscriptionTier }) {
  return (
    <ul className="space-y-1 rounded-md border bg-muted/40 p-3 text-xs">
      {SUBSCRIPTION_TIERS[tier].features.map((feature) => (
        <li key={feature} className="flex items-center gap-2">
          <Check className="h-3 w-3 shrink-0 text-success" aria-hidden />
          {feature}
        </li>
      ))}
    </ul>
  );
}
