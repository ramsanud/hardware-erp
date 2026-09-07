import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, ShieldAlert } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { ApiError } from '@/shared/types/api';
import { AUTH_ROUTES } from '../constants';
import { authService } from '../services/authService';
import { resetPasswordSchema, type ResetPasswordValues } from '../validation/schemas';
import { PASSWORD_HINT, PasswordInput } from '../forms/PasswordFields';
import { useToast } from '../hooks/useToast';

export function ResetPasswordPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);

  /*
   * BUG-FE-025. The token is lifted out of the URL on the first render and
   * kept in a ref, then the query string is rewritten away with replace:true.
   *
   * Two things were wrong with reading it straight off the URL on every
   * render. First, a single-use credential sat in the address bar, in
   * browser history, in the Referer header of every subsequent request, and
   * in whatever syncs that history between the user's devices. Second - the
   * reported symptom - the tokened URL stayed in the history stack, so after
   * finishing the reset and landing on /login, one press of Back (or the
   * Android back gesture) re-entered the reset form with a token the server
   * had already burned. The user saw the password screen they thought they
   * had finished with, and submitting it failed with no useful explanation.
   *
   * Replacing the entry means Back from /login lands on a tokenless
   * /reset-password, which renders the "this link is not valid" card below
   * with a way forward, instead of a dead form. The ref (not state) is what
   * makes this safe: the token has to outlive the URL it came from.
   */
  const tokenRef = useRef<string | null>(searchParams.get('token'));
  const token = tokenRef.current;

  useEffect(() => {
    if (!searchParams.has('token')) return;
    const next = new URLSearchParams(searchParams);
    next.delete('token');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const {
    register, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  });

  const submit = handleSubmit(async (values) => {
    if (!token) return;
    setFormError(null);
    try {
      await authService.resetPassword({ token, newPassword: values.newPassword });
      // The token is spent. Dropping it here means that even if this entry is
      // somehow revisited, the page renders the invalid-link card rather than
      // a form that can only ever fail.
      tokenRef.current = null;
      toast.success('Password updated. Please sign in.');
      // state: null so nothing from this flow - a stale `from`, a `registered`
      // banner - survives onto a login screen that must open clean.
      navigate(AUTH_ROUTES.login, { replace: true, state: null });
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof ResetPasswordValues, { message });
        });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  // A link opened without a token, or with a truncated one, gets a clear
  // explanation rather than a form that will always fail.
  if (!token) {
    return (
      <Card className="mx-auto w-full max-w-md">
        <CardHeader>
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-destructive/10">
            <ShieldAlert className="h-5 w-5 text-destructive" aria-hidden />
          </div>
          <CardTitle>This link is not valid</CardTitle>
          <CardDescription>
            The reset link is missing its token. It may have been broken by your email
            client. Request a new one.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          <Button className="w-full" asChild>
            <Link to={AUTH_ROUTES.forgotPassword} replace>Request a new link</Link>
          </Button>
          <Button variant="ghost" className="w-full" asChild>
            <Link to={AUTH_ROUTES.login} replace>
              <ArrowLeft className="h-4 w-4" />
              Back to sign in
            </Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="mx-auto w-full max-w-md">
      <CardHeader>
        <CardTitle>Choose a new password</CardTitle>
        <CardDescription>This link can only be used once.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4" noValidate>
          {formError ? (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          ) : null}

          <PasswordInput
            id="newPassword" label="New password" hint={PASSWORD_HINT}
            error={errors.newPassword} registration={register('newPassword')}
          />
          <PasswordInput
            id="confirmPassword" label="Confirm new password"
            error={errors.confirmPassword} registration={register('confirmPassword')}
          />

          <Button type="submit" className="w-full" loading={isSubmitting}>
            Set new password
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
