import * as React from 'react';
import { useEffect, useRef, useState } from 'react';
import { cn } from '@/shared/lib/utils';

export interface NumberInputProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> {
  value: number;
  onChange: (value: number) => void;
}

/**
 * A native `<input type="number">` bound to a react-hook-form field whose
 * default is 0 shows "0100" when the user types "100" - the browser never
 * strips the leading zero as you type, only some browsers on blur. This
 * renders as text (numeric keypad on mobile via inputMode) so it can strip
 * leading zeros itself and show a genuinely empty field instead of "0",
 * while still handing the caller a real number - 0 when the field is empty.
 */
export const NumberInput = React.forwardRef<HTMLInputElement, NumberInputProps>(
  ({ value, onChange, className, onBlur, ...props }, ref) => {
    const [text, setText] = useState(() => (value ? String(value) : ''));
    const lastExternal = useRef(value);

    useEffect(() => {
      if (value !== lastExternal.current) {
        lastExternal.current = value;
        setText(value ? String(value) : '');
      }
    }, [value]);

    return (
      <input
        ref={ref}
        type="text"
        inputMode="decimal"
        className={cn(
          'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-base ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 md:text-sm',
          'aria-[invalid=true]:border-destructive aria-[invalid=true]:focus-visible:ring-destructive',
          className,
        )}
        value={text}
        onChange={(event) => {
          let raw = event.target.value.replace(/[^\d.]/g, '');
          const firstDot = raw.indexOf('.');
          if (firstDot !== -1) {
            raw = raw.slice(0, firstDot + 1) + raw.slice(firstDot + 1).replace(/\./g, '');
          }
          raw = raw.replace(/^0+(?=\d)/, '');

          setText(raw);
          const next = raw === '' || raw === '.' ? 0 : Number(raw);
          lastExternal.current = next;
          onChange(next);
        }}
        onBlur={(event) => {
          // An empty field commits as 0 without forcing "0" back into view -
          // the user's own next keystroke starts clean, same as a fresh field.
          onBlur?.(event);
        }}
        {...props}
      />
    );
  },
);
NumberInput.displayName = 'NumberInput';
