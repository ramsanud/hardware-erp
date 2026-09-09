import type { ReactNode } from 'react';
import { Label } from '@/shared/components/ui/label';
import { cn } from '@/shared/lib/utils';

interface FormFieldProps {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  /**
   * CR-069. Lets a live check report a *pass*, not just a failure - the
   * registration form's shop-name lookup had no way to say "available" in
   * anything but muted grey, which reads as neutral advice rather than a
   * cleared check. Defaults to 'muted', so every existing call site is
   * unchanged.
   */
  hintTone?: 'muted' | 'success';
  required?: boolean;
  className?: string;
  children: ReactNode;
}

/**
 * Wraps every input so the label, error and hint are associated correctly.
 * aria-describedby points at whichever of the two is present, which is what
 * makes the error audible to a screen reader.
 */
export function FormField({
  id, label, error, hint, hintTone = 'muted', required, className, children,
}: FormFieldProps) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <div className={cn('space-y-1.5', className)}>
      <Label htmlFor={id}>
        {label}
        {required ? <span className="ml-0.5 text-destructive" aria-hidden>*</span> : null}
      </Label>
      <div aria-describedby={describedBy}>{children}</div>
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-sm text-destructive">{error}</p>
      ) : hint ? (
        <p
          id={`${id}-hint`}
          className={cn('text-sm', hintTone === 'success' ? 'font-medium text-success' : 'text-muted-foreground')}
        >
          {hint}
        </p>
      ) : null}
    </div>
  );
}
