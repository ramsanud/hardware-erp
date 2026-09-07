import * as React from 'react';
import { X } from 'lucide-react';
import { Input, type InputProps } from '@/shared/components/ui/input';
import { cn } from '@/shared/lib/utils';

export interface ClearableInputProps extends InputProps {
  /** Called after the field has been emptied, so RHF/parent state stays in step with the DOM. */
  onClear?: () => void;
  /**
   * Extra right-hand padding, in Tailwind units, already claimed by another
   * control inside the same relative wrapper - a password show/hide eye, a
   * unit suffix. The clear button is offset by the same amount so the two
   * never sit on top of each other.
   */
  trailingSlot?: number;
}

/**
 * An input that offers a one-tap clear (x) once it has a value.
 *
 * Why this exists as a primitive rather than a per-form snippet: on a phone,
 * emptying a field otherwise means summoning the keyboard and holding
 * backspace, which is the slowest interaction in the app and the one users hit
 * most (a mistyped mobile number at the login screen). SearchInput already had
 * its own copy of this button; everything else had nothing.
 *
 * It is deliberately uncontrolled-friendly. react-hook-form registers plain
 * inputs, so clearing has to look to React exactly like the user selecting all
 * and deleting - hence the native value setter plus a bubbled `input` event
 * below, not a synthetic object cast to ChangeEvent. Anything less and RHF's
 * subscription never fires, the field looks empty but validates against its
 * old value, and the form submits data the user cannot see.
 */
const ClearableInput = React.forwardRef<HTMLInputElement, ClearableInputProps>(
  ({ className, onClear, trailingSlot = 0, disabled, readOnly, ...props }, ref) => {
    const innerRef = React.useRef<HTMLInputElement | null>(null);
    // Mirrors the DOM value so the button appears for controlled and
    // uncontrolled callers alike, without forcing either into the other shape.
    const [hasValue, setHasValue] = React.useState(
      () => String(props.value ?? props.defaultValue ?? '').length > 0,
    );

    const setRefs = (node: HTMLInputElement | null) => {
      innerRef.current = node;
      if (typeof ref === 'function') ref(node);
      else if (ref) ref.current = node;
    };

    React.useEffect(() => {
      if (props.value !== undefined) setHasValue(String(props.value).length > 0);
    }, [props.value]);

    const handleClear = () => {
      const node = innerRef.current;
      if (!node) return;
      const setter = Object.getOwnPropertyDescriptor(
        window.HTMLInputElement.prototype, 'value',
      )?.set;
      setter?.call(node, '');
      node.dispatchEvent(new Event('input', { bubbles: true }));
      setHasValue(false);
      onClear?.();
      // Keeping focus is the point of the control: the user is clearing in
      // order to retype, and a phone that dismissed the keyboard here would
      // cost them the tap it just saved.
      node.focus();
    };

    // Never offered where clearing is meaningless or destructive-by-surprise:
    // a disabled or read-only field, or one the user has not typed into.
    const showClear = hasValue && !disabled && !readOnly;

    return (
      <>
        <Input
          {...props}
          ref={setRefs}
          disabled={disabled}
          readOnly={readOnly}
          onChange={(event) => {
            setHasValue(event.target.value.length > 0);
            props.onChange?.(event);
          }}
          className={cn(
            // The trailing control's corner is reserved even before the clear
            // button appears, so text never slides under the eye toggle on an
            // empty field and then jumps when the first character lands.
            trailingSlot ? (showClear ? 'pr-20' : 'pr-10') : showClear ? 'pr-10' : undefined,
            className,
          )}
        />
        {showClear ? (
          <button
            type="button"
            // tabIndex -1: keyboard users already have Ctrl/Cmd+A + Delete and
            // do not need an extra stop between every field and the next.
            tabIndex={-1}
            onClick={handleClear}
            aria-label="Clear"
            className={cn(
              // A fixed 40x40 box, not padding around a 16px icon: padding
              // gave a 32px target, under every touch-size guideline, and the
              // difference is felt on the one control whose entire purpose is
              // to save a phone user from the keyboard. 40px is also exactly
              // the input's own height, so the box fills it edge to edge.
              'absolute top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center',
              'rounded-md text-muted-foreground transition-colors hover:text-foreground',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              trailingSlot ? 'right-10' : 'right-0',
            )}
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        ) : null}
      </>
    );
  },
);
ClearableInput.displayName = 'ClearableInput';

export { ClearableInput };
