import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Lock, User } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { loginSchema, type LoginValues } from '../validation/schemas';
import { authService } from '../services/authService';
import { TurnstileWidget } from '../components/TurnstileWidget';
import type { CaptchaConfigResponse } from '../types';

interface LoginFormProps {
  onSubmit: (values: LoginValues, captchaToken: string | null) => Promise<void>;
}

export function LoginForm({ onSubmit }: LoginFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

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
    defaultValues: { identifier: '', password: '' },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values, captchaToken);
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
          <Input
            id="identifier"
            autoComplete="username"
            inputMode="text"
            autoFocus
            placeholder="9876543210"
            className="pl-9"
            aria-invalid={Boolean(errors.identifier)}
            {...register('identifier')}
          />
        </div>
      </FormField>

      <FormField id="password" label="Password" error={errors.password?.message} required>
        <div className="relative">
          <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            id="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            className="pl-9 pr-10"
            aria-invalid={Boolean(errors.password)}
            {...register('password')}
          />
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

      {captchaRequired && captcha?.siteKey ? (
        <TurnstileWidget
          siteKey={captcha.siteKey}
          onToken={setCaptchaToken}
          resetSignal={captchaReset}
        />
      ) : null}

      <Button type="submit" className="w-full" loading={isSubmitting}
              disabled={captchaRequired && !captchaToken}>
        Sign in
      </Button>
    </form>
  );
}
