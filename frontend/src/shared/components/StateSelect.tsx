import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/components/ui/select';
import { INDIAN_STATES } from '@/shared/data/indianStates';

interface StateSelectProps {
  id: string;
  /** The 2-digit GST state code, e.g. "33". Empty string when nothing is chosen. */
  value: string;
  onChange: (code: string) => void;
  placeholder?: string;
  /** Adds a "Not specified" entry, for the wizards where the field is optional. */
  clearable?: boolean;
}

const NONE = '__none__';

/**
 * Pick a state, store its GST code.
 *
 * Customer and Supplier already paired a state dropdown with the code field;
 * the Invoice and Quotation wizards asked for the raw two digits instead
 * ("Customer state code (optional)", placeholder "29"), which meant knowing
 * off-hand that Tamil Nadu is 33 - and getting it wrong silently flips the
 * invoice between CGST+SGST and IGST. Same list, same behaviour, one
 * component now, so a fifth caller cannot drift again.
 */
export function StateSelect({ id, value, onChange, placeholder = 'Select a state', clearable }: StateSelectProps) {
  const selected = INDIAN_STATES.find((state) => state.code === value);

  return (
    <Select
      value={selected ? selected.code : (clearable ? NONE : '')}
      onValueChange={(next) => {
        // Radix re-fires onValueChange('') when the committed value has no
        // matching item in the current children - see BUG-FE-007.
        if (next === '') return;
        onChange(next === NONE ? '' : next);
      }}
    >
      <SelectTrigger id={id}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {clearable ? <SelectItem value={NONE}>Not specified</SelectItem> : null}
        {INDIAN_STATES.map((state) => (
          <SelectItem key={state.code} value={state.code}>
            {state.name}
            <span className="ml-2 tabular text-xs text-muted-foreground">{state.code}</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
