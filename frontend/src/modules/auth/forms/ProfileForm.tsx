import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Pencil } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { profileSchema, type ProfileValues } from '../validation/schemas';
import type { UserResponse } from '../types';

interface ProfileFormProps {
  user: UserResponse;
  onSubmit: (values: ProfileValues) => Promise<void>;
}

/** One labelled value in read mode. */
function ReadRow({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-b py-3 last:border-b-0">
      <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</span>
      <span className="text-sm">{value}</span>
      {hint ? <span className="text-xs text-muted-foreground">{hint}</span> : null}
    </div>
  );
}

export function ProfileForm({ user, onSubmit }: ProfileFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  // Read-only until Edit is pressed. Previously these inputs were always live,
  // so the name field was one stray keystroke away from being changed - and
  // with no Save needed to notice, the only signal was the button un-graying.
  const [editing, setEditing] = useState(false);

  const {
    register, handleSubmit, setError, reset, formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user.fullName, email: user.email ?? '' },
  });

  const cancel = () => {
    reset({ fullName: user.fullName, email: user.email ?? '' });
    setFormError(null);
    setEditing(false);
  };

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
      // Re-baseline so the form is no longer dirty against the saved values.
      reset(values);
      setEditing(false);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof ProfileValues, { message });
        });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  if (!editing) {
    return (
      <div className="space-y-4">
        <div className="flex flex-col">
          <ReadRow label="Full name" value={user.fullName} />
          <ReadRow label="Email" value={user.email || 'Not set'}
                   hint="Used for password reset links." />
          <ReadRow label="Mobile number" value={user.mobileNo}
                   hint="Only the owner can change this." />
        </div>
        <Button type="button" variant="outline" onClick={() => setEditing(true)}>
          <Pencil className="h-4 w-4" />
          Edit details
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      <FormField id="fullName" label="Full name" error={errors.fullName?.message} required>
        <Input id="fullName" autoFocus aria-invalid={Boolean(errors.fullName)} {...register('fullName')} />
      </FormField>

      <FormField id="email" label="Email" error={errors.email?.message}
                 hint="Used for password reset links.">
        <Input id="email" type="email" autoComplete="email"
               aria-invalid={Boolean(errors.email)} {...register('email')} />
      </FormField>

      {/* Mobile, role and status stay read-only: PUT /auth/me accepts only name
          and email, so rendering them as editable would lie. */}
      <FormField id="mobileNoReadOnly" label="Mobile number"
                 hint="Only the owner can change this.">
        <Input id="mobileNoReadOnly" value={user.mobileNo} readOnly disabled />
      </FormField>

      <div className="flex flex-col-reverse gap-2 sm:flex-row">
        <Button type="button" variant="outline" onClick={cancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
          Save changes
        </Button>
      </div>
    </form>
  );
}
