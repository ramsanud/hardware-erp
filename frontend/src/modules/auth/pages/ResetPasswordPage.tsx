import { useState } from 'react';
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
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);

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
      toast.success('Password updated. Please sign in.');
      navigate(AUTH_ROUTES.login, { replace: true });
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
      <Card>
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
            <Link to={AUTH_ROUTES.forgotPassword}>Request a new link</Link>
          </Button>
          <Button variant="ghost" className="w-full" asChild>
            <Link to={AUTH_ROUTES.login}>
              <ArrowLeft className="h-4 w-4" />
              Back to sign in
            </Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
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
