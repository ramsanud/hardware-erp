import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Lock, User } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Label } from '@/shared/components/ui/label';
import { ClearableInput } from '@/shared/components/ui/clearable-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { AUTH_ROUTES } from '../constants';
import { loginSchema, type LoginValues } from '../validation/schemas';
import { authService } from '../services/authService';
import { TurnstileWidget } from '../components/TurnstileWidget';
import type { CaptchaConfigResponse } from '../types';

/**
 * CR-069. "Remember me" stores the *identifier only* - never a token.
 *
 * The obvious reading of this control is "keep me signed in longer", and that
 * is deliberately not what it does: session lifetime is the refresh token's,
 * which is an HttpOnly cookie the server owns, and hard rule 9 keeps the
 * access token in memory. Prefilling the mobile number is the part that can be
 * honoured honestly on the client, so the label says exactly that rather than
 * implying a longer session the server never granted.
 */
const REMEMBERED_IDENTIFIER_KEY = 'hardware-erp-remembered-identifier';

const readRememberedIdentifier = () => {
  // Private-mode browsers and locked-down profiles throw on access rather
  // than returning null, which would take the whole sign-in form down.
  try {
    return localStorage.getItem(REMEMBERED_IDENTIFIER_KEY) ?? '';
  } catch {
    return '';
  }
};

interface LoginFormProps {
  onSubmit: (values: LoginValues, captchaToken: string | null) => Promise<void>;
}

export function LoginForm({ onSubmit }: LoginFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [rememberedIdentifier] = useState(readRememberedIdentifier);
  const [rememberMe, setRememberMe] = useState(Boolean(rememberedIdentifier));

  // null until the server answers, so the form neither flashes a challenge on
  // installs without one nor lets a submit through before we know.
  const [captcha, setCaptcha] = useState<CaptchaConfigResponse | null>(null);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [captchaReset, setCaptchaReset] = useState(0);

  useEffect(() => {
    authService.captchaConfig()
      .then(setCaptcha)
      // A failed config lookup must not block sign-in: the server is the
      // enforcement point and rejects a missing token itself when required.
      .catch(() => setCaptcha({ enabled: false, siteKey: null }));
  }, []);

  const captchaRequired = Boolean(captcha?.enabled && captcha.siteKey);

  const {
    register, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: rememberedIdentifier, password: '' },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values, captchaToken);
      // Only after the credentials are accepted, so a typo is never the thing
      // that gets remembered and prefilled on the next visit.
      try {
        if (rememberMe) localStorage.setItem(REMEMBERED_IDENTIFIER_KEY, values.identifier);
        else localStorage.removeItem(REMEMBERED_IDENTIFIER_KEY);
      } catch {
        // Storage being unavailable must never fail a successful sign-in.
      }
    } catch (error) {
      // A Turnstile token is single-use: after any failed attempt the old one
      // is spent, so the widget must be re-run or the next try fails on the
      // challenge rather than the credentials.
      if (captchaRequired) {
        setCaptchaToken(null);
        setCaptchaReset((n) => n + 1);
      }
      if (error instanceof ApiError) {
        // Field errors only ever come back for malformed input. A rejected
        // credential is deliberately a single generic message with no hint
        // about which half was wrong.
        if (error.fieldErrors) {
          Object.entries(error.fieldErrors).forEach(([field, message]) => {
            setError(field as keyof LoginValues, { message });
          });
        }
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      ) : null}

      <FormField id="identifier" label="Mobile number or email" error={errors.identifier?.message} required>
        <div className="relative">
          <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <ClearableInput
            id="identifier"
            autoComplete="username"
            inputMode="text"
            // A returning user whose number is already filled in wants the
            // caret in the password box, not back on a field they have done.
            autoFocus={!rememberedIdentifier}
            placeholder="9876543210"
            className="pl-9"
            aria-invalid={Boolean(errors.identifier)}
            aria-describedby={errors.identifier ? 'identifier-error' : undefined}
            {...register('identifier')}
          />
        </div>
      </FormField>

      <FormField id="password" label="Password" error={errors.password?.message} required>
        <div className="relative">
          <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          {/* trailingSlot reserves the eye toggle's corner so the clear
              button lands beside it, never on top of it. */}
          <ClearableInput
            id="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            className="pl-9"
            trailingSlot={1}
            autoFocus={Boolean(rememberedIdentifier)}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? 'password-error' : undefined}
            {...register('password')}
          />
          <button
            type="button"
            onClick={() => setShowPassword((visible) => !visible)}
            className="absolute right-0 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center
                       justify-center rounded-md text-muted-foreground transition-colors
                       hover:text-foreground focus-visible:outline-none focus-visible:ring-2
                       focus-visible:ring-ring"
            aria-label={showPassword ? 'Hide password' : 'Show password'}
          >
            {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>
      </FormField>

      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Checkbox
            id="rememberMe"
            checked={rememberMe}
            onCheckedChange={(checked) => setRememberMe(checked === true)}
          />
          {/* htmlFor + the Checkbox's own id is what makes the label click-target and the screen-reader name work. */}
          <Label htmlFor="rememberMe" className="cursor-pointer text-sm font-normal text-muted-foreground">
            Remember my number
          </Label>
        </div>
        <Link
          to={AUTH_ROUTES.forgotPassword}
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          Forgot password?
        </Link>
      </div>

      {captchaRequired && captcha?.siteKey ? (
        <TurnstileWidget
          siteKey={captcha.siteKey}
          onToken={setCaptchaToken}
          resetSignal={captchaReset}
        />
      ) : null}

      <Button type="submit" variant="gradient" size="lg" className="w-full" loading={isSubmitting}
              disabled={captchaRequired && !captchaToken}>
        Sign in
      </Button>
    </form>
  );
}
