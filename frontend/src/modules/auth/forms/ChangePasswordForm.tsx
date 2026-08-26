import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { ApiError } from '@/shared/types/api';
import { changePasswordSchema, type ChangePasswordValues } from '../validation/schemas';
import { PASSWORD_HINT, PasswordInput } from './PasswordFields';

interface ChangePasswordFormProps {
  onSubmit: (values: ChangePasswordValues) => Promise<void>;
  submitLabel?: string;
}

export function ChangePasswordForm({
  onSubmit, submitLabel = 'Change password',
}: ChangePasswordFormProps) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        // The backend answers a bad current password with WRONG_PASSWORD, which
        // belongs on that field rather than in a banner.
        if (error.code === 'WRONG_PASSWORD') {
          setError('currentPassword', { message: error.message });
          return;
        }
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof ChangePasswordValues, { message });
        });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      <PasswordInput
        id="currentPassword" label="Current password"
        autoComplete="current-password"
        error={errors.currentPassword}
        registration={register('currentPassword')}
      />
      <PasswordInput
        id="newPassword" label="New password" hint={PASSWORD_HINT}
        error={errors.newPassword}
        registration={register('newPassword')}
      />
      <PasswordInput
        id="confirmPassword" label="Confirm new password"
        error={errors.confirmPassword}
        registration={register('confirmPassword')}
      />

      <Alert>
        <AlertDescription className="text-sm">
          Changing your password signs you out on every device, including this one.
        </AlertDescription>
      </Alert>

      <Button type="submit" className="w-full" loading={isSubmitting}>
        {submitLabel}
      </Button>
    </form>
  );
}
