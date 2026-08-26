import { useState } from 'react';
import type { FieldError, UseFormRegisterReturn } from 'react-hook-form';
import { Eye, EyeOff } from 'lucide-react';
import { Input } from '@/shared/components/ui/input';
import { FormField } from '@/shared/components/FormField';

interface PasswordInputProps {
  id: string;
  label: string;
  error?: FieldError;
  hint?: string;
  autoComplete?: string;
  registration: UseFormRegisterReturn;
}

/**
 * Shared so the show/hide toggle, autocomplete hint and aria wiring are
 * identical on every password field in the module.
 */
export function PasswordInput({
  id, label, error, hint, autoComplete = 'new-password', registration,
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false);

  return (
    <FormField id={id} label={label} error={error?.message} hint={hint} required>
      <div className="relative">
        <Input
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          className="pr-10"
          aria-invalid={Boolean(error)}
          {...registration}
        />
        <button
          type="button"
          onClick={() => setVisible((shown) => !shown)}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded-sm p-1.5 text-muted-foreground hover:text-foreground"
          aria-label={visible ? 'Hide password' : 'Show password'}
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
    </FormField>
  );
}

export const PASSWORD_HINT = 'At least 8 characters, with one letter and one number.';
