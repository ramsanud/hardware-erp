import { forwardRef, useEffect, useState, type InputHTMLAttributes } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ArrowLeft, ArrowRight, Building2, Check, Eye, EyeOff, Lock, Mail, Phone, User,
} from 'lucide-react';
import { enterAdvances } from '@/shared/hooks/useEnterAdvances';
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
import { cn } from '@/shared/lib/utils';
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

const STEPS = ['Your shop', 'Sign-in details', 'Plan & agreement'] as const;

const STEP_FIELDS: Record<number, (keyof RegisterValues)[]> = {
  0: ['shopName', 'ownerFullName'],
  1: ['mobileNo', 'email', 'password'],
};

/**
 * Three short steps instead of one long scroll. Same fields, same
 * validation, same registration call - only the presentation changed,
 * mirroring SupplierWizard's proven step-indicator + sticky Back/Next
 * pattern rather than inventing a new one.
 */
export function RegisterPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const {
    register, control, handleSubmit, trigger, watch, formState: { errors, isSubmitting },
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

  const goNext = async () => {
    const fields = STEP_FIELDS[step];
    if (fields && !(await trigger(fields))) return;
    setStep((current) => Math.min(current + 1, STEPS.length - 1));
  };

  const goBack = () => setStep((current) => Math.max(current - 1, 0));

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
        // A duplicate mobile/email/shop-name error was raised against a
        // field on an earlier step - send the owner back to it rather than
        // leaving them stuck on the consent step with no visible cause.
        const lower = caught.message.toLowerCase();
        if (lower.includes('mobile') || lower.includes('email')) setStep(1);
        else if (lower.includes('shop')) setStep(0);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <Card className="mx-auto w-full max-w-xl">
      <CardHeader>
        <CardTitle>Create your account</CardTitle>
        <CardDescription>Register a new hardware shop - one shop, one owner login.</CardDescription>
      </CardHeader>
      <CardContent>
        <div onKeyDown={enterAdvances(() => { void goNext(); })}>
          <ol className="mb-6 flex items-center gap-2 text-sm">
            {STEPS.map((label, index) => (
              <li key={label} className="flex flex-1 items-center gap-2">
                <span
                  className={cn(
                    'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs font-semibold',
                    index < step && 'border-primary bg-primary text-primary-foreground',
                    index === step && 'border-primary text-primary',
                    index > step && 'border-muted-foreground/30 text-muted-foreground',
                  )}
                >
                  {index < step ? <Check className="h-3.5 w-3.5" /> : index + 1}
                </span>
                <span className={cn('hidden truncate sm:inline', index === step ? 'font-medium' : 'text-muted-foreground')}>
                  {label}
                </span>
                {index < STEPS.length - 1 ? (
                  <span className={cn('h-px flex-1', index < step ? 'bg-primary' : 'bg-border')} />
                ) : null}
              </li>
            ))}
          </ol>

          {formError ? (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          ) : null}

          {step === 0 ? (
            <div className="space-y-4">
              <FormField id="shopName" label="Shop name" error={errors.shopName?.message} required
                         hint={slugStatus === 'checking' ? 'Checking availability…'
                           : slugStatus === 'available' ? 'Available'
                           : slugStatus === 'taken' ? 'A shop with a very similar name already exists' : undefined}>
                <IconInput icon={Building2} id="shopName" autoFocus {...register('shopName')} />
              </FormField>

              <FormField id="ownerFullName" label="Your name" error={errors.ownerFullName?.message} required>
                <IconInput icon={User} id="ownerFullName" {...register('ownerFullName')} />
              </FormField>
            </div>
          ) : null}

          {step === 1 ? (
            <div className="space-y-4">
              <FormField id="mobileNo" label="Mobile number" error={errors.mobileNo?.message} required>
                <IconInput icon={Phone} id="mobileNo" autoFocus inputMode="tel" placeholder="9876543210"
                           {...register('mobileNo')} />
              </FormField>

              <FormField id="email" label="Email address" error={errors.email?.message} required>
                <IconInput icon={Mail} id="email" type="email" inputMode="email" autoComplete="email"
                           placeholder="you@yourshop.com" {...register('email')} />
              </FormField>

              <FormField id="password" label="Password" error={errors.password?.message} required
                         hint="At least 8 characters, with a letter and a number.">
                <div className="relative">
                  <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
                  <Input id="password" type={showPassword ? 'text' : 'password'} className="pl-9 pr-10"
                         aria-invalid={Boolean(errors.password)} {...register('password')} />
                  <button
                    type="button"
                    onClick={() => setShowPassword((visible) => !visible)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded-sm p-1.5 text-muted-foreground hover:text-foreground"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </FormField>
            </div>
          ) : null}

          {step === 2 ? (
            <div className="space-y-4">
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
            </div>
          ) : null}

          <div className="mt-6 flex items-center justify-between border-t pt-4">
            <Button type="button" variant="outline" onClick={step === 0 ? undefined : goBack} disabled={isSubmitting}
                    tabIndex={step === 0 ? -1 : 0} aria-hidden={step === 0}
                    className={step === 0 ? 'invisible' : undefined}>
              <ArrowLeft className="h-4 w-4" />
              Back
            </Button>
            {step < STEPS.length - 1 ? (
              <Button type="button" onClick={goNext}>
                Next
                <ArrowRight className="h-4 w-4" />
              </Button>
            ) : (
              <Button type="button" onClick={submit} loading={isSubmitting} disabled={!consent.termsAccepted}>
                Create account
              </Button>
            )}
          </div>
        </div>

        <div className="mt-6 text-center text-sm">
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

/**
 * A leading-icon input, the same manual relative/absolute pattern the
 * password eye-toggle already uses.
 *
 * MUST forward its ref to the underlying <Input>: react-hook-form's
 * register() returns a ref callback that has to reach the real DOM node
 * for the field to be tracked at all. A plain (non-forwardRef) function
 * component silently drops a ref passed to it - React treats ref as a
 * reserved prop for such components and never delivers it - which left
 * every IconInput-wrapped field permanently untracked: watch('shopName')
 * returned undefined instead of '', and the debounced-slug-check effect
 * crashed calling .trim() on it.
 */
const IconInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { icon: typeof Building2 }>(
  ({ icon: Icon, className, ...props }, ref) => (
    <div className="relative">
      <Icon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input ref={ref} className={cn('pl-9', className)} {...props} />
    </div>
  ),
);
IconInput.displayName = 'IconInput';
