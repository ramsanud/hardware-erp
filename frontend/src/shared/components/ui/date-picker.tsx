import * as React from 'react';
import { CalendarIcon } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Calendar } from '@/shared/components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '@/shared/components/ui/popover';
import { cn } from '@/shared/lib/utils';

function parseISODate(value?: string): Date | undefined {
  if (!value) return undefined;
  const [y, m, d] = value.split('-').map(Number);
  if (!y || !m || !d) return undefined;
  return new Date(y, m - 1, d);
}

function toISODate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatDisplay(date: Date): string {
  return date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export interface DatePickerProps {
  id?: string;
  /** ISO `yyyy-MM-dd`, matching the native `<input type="date">` this replaces. */
  value: string;
  onChange: (value: string) => void;
  /** ISO `yyyy-MM-dd` bounds, same as the native input's min/max. */
  min?: string;
  max?: string;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

export function DatePicker({ id, value, onChange, min, max, placeholder = 'Pick a date', disabled, className }: DatePickerProps) {
  const [open, setOpen] = React.useState(false);
  const selected = parseISODate(value);
  const minDate = parseISODate(min);
  const maxDate = parseISODate(max);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          disabled={disabled}
          className={cn(
            'control-surface-inset h-10 w-full justify-start gap-2 border-input bg-background px-3 text-left font-normal hover:bg-background',
            !selected && 'text-muted-foreground',
            className,
          )}
        >
          <CalendarIcon className="h-4 w-4 shrink-0 opacity-60" aria-hidden />
          {selected ? formatDisplay(selected) : placeholder}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto p-0">
        <Calendar
          selected={selected}
          minDate={minDate}
          maxDate={maxDate}
          onSelect={(date) => {
            onChange(toISODate(date));
            setOpen(false);
          }}
        />
      </PopoverContent>
    </Popover>
  );
}
