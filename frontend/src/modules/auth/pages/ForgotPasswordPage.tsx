import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, MailCheck, User } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { FormField } from '@/shared/components/FormField';
import { AUTH_ROUTES } from '../constants';
import { authService } from '../services/authService';
import { forgotPasswordSchema, type ForgotPasswordValues } from '../validation/schemas';
import { useToast } from '../hooks/useToast';

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false);
  const toast = useToast();

  const {
    register, handleSubmit, getValues, formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { identifier: '' },
  });

  const submit = handleSubmit(async (values) => {
    try {
      await authService.forgotPassword(values);
      // The backend answers identically whether or not the account exists, and
      // so does this screen. Anything else would leak which accounts are real.
      setSent(true);
    } catch (error) {
      toast.error(error, 'Could not send the reset link. Please try again.');
    }
  });

  if (sent) {
    return (
      <Card className="mx-auto w-full max-w-sm">
        <CardHeader>
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-success/10">
            <MailCheck className="h-5 w-5 text-success" aria-hidden />
          </div>
          <CardTitle>Check your email</CardTitle>
          <CardDescription>
            If an account exists for {getValues('identifier')}, a reset link has been sent.
            It expires in 30 minutes and can be used once.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Alert>
            <AlertDescription className="text-sm">
              No email? The account may not have an email address on record. Ask the
              shop owner to reset your password instead.
            </AlertDescription>
          </Alert>
          <Button variant="outline" className="w-full" asChild>
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
    <Card className="mx-auto w-full max-w-sm">
      <CardHeader>
        <CardTitle>Reset your password</CardTitle>
        <CardDescription>
          Enter your mobile number or email and we will send a reset link.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormField id="identifier" label="Mobile number or email"
                     error={errors.identifier?.message} required>
            <div className="relative">
              <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <Input id="identifier" autoFocus autoComplete="username"
                     placeholder="9876543210" className="pl-9"
                     aria-invalid={Boolean(errors.identifier)} {...register('identifier')} />
            </div>
          </FormField>

          <Button type="submit" className="w-full" loading={isSubmitting}>
            Send reset link
          </Button>

          <Button variant="ghost" className="w-full" asChild>
            <Link to={AUTH_ROUTES.login}>
              <ArrowLeft className="h-4 w-4" />
              Back to sign in
            </Link>
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
