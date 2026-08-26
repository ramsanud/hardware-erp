import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { ApiError } from '@/shared/types/api';
import {
  resetUserPasswordSchema, type ResetUserPasswordValues,
} from '../validation/schemas';
import { PASSWORD_HINT, PasswordInput } from './PasswordFields';

interface ResetUserPasswordFormProps {
  userName: string;
  onSubmit: (values: ResetUserPasswordValues) => Promise<void>;
  onCancel: () => void;
}

export function ResetUserPasswordForm({
  userName, onSubmit, onCancel,
}: ResetUserPasswordFormProps) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<ResetUserPasswordValues>({
    resolver: zodResolver(resetUserPasswordSchema),
    defaultValues: { newPassword: '' },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof ResetUserPasswordValues, { message });
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

      <p className="text-sm text-muted-foreground">
        Setting a temporary password for <span className="font-medium text-foreground">{userName}</span>.
      </p>

      <PasswordInput
        id="newPassword" label="Temporary password" hint={PASSWORD_HINT}
        error={errors.newPassword}
        registration={register('newPassword')}
      />

      <Alert variant="warning">
        <AlertDescription className="text-sm">
          This signs the user out everywhere and forces them to choose a new password
          at their next sign-in. Give them the temporary password in person.
        </AlertDescription>
      </Alert>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>Reset password</Button>
      </DialogFooter>
    </form>
  );
}
