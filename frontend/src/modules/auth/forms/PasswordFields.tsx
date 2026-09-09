import { useState } from 'react';
import type { FieldError, UseFormRegisterReturn } from 'react-hook-form';
import { Eye, EyeOff } from 'lucide-react';
import { ClearableInput } from '@/shared/components/ui/clearable-input';
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
        {/* trailingSlot=1 parks the clear button to the left of the eye so the
            two controls never overlap - the show/hide toggle keeps its usual
            corner and its behaviour is untouched. */}
        <ClearableInput
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          trailingSlot={1}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
          {...registration}
        />
        <button
          type="button"
          onClick={() => setVisible((shown) => !shown)}
          className="absolute right-0 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center
                     justify-center rounded-md text-muted-foreground transition-colors
                     hover:text-foreground focus-visible:outline-none focus-visible:ring-2
                     focus-visible:ring-ring"
          aria-label={visible ? 'Hide password' : 'Show password'}
        >
          {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
    </FormField>
  );
}

export const PASSWORD_HINT = 'At least 8 characters, with one letter and one number.';
