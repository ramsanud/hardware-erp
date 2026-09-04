import * as React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/components/ui/select';
import { cn } from '@/shared/lib/utils';

const WEEKDAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function stripTime(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

export interface CalendarProps {
  selected?: Date;
  onSelect: (date: Date) => void;
  minDate?: Date;
  maxDate?: Date;
  className?: string;
}

/**
 * Hand-rolled rather than react-day-picker - the project has no date-fns
 * dependency and this only needs a plain month grid with year/month jumps,
 * not the full range/multi-select surface that library covers.
 */
export function Calendar({ selected, onSelect, minDate, maxDate, className }: CalendarProps) {
  const today = new Date();
  const initial = selected ?? today;
  const [viewYear, setViewYear] = React.useState(initial.getFullYear());
  const [viewMonth, setViewMonth] = React.useState(initial.getMonth());

  React.useEffect(() => {
    if (selected) {
      setViewYear(selected.getFullYear());
      setViewMonth(selected.getMonth());
    }
  }, [selected]);

  const firstOfMonth = new Date(viewYear, viewMonth, 1);
  const startWeekday = firstOfMonth.getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();

  const cells: { date: Date; outside: boolean }[] = [];
  for (let i = startWeekday - 1; i >= 0; i--) {
    cells.push({ date: new Date(viewYear, viewMonth, -i), outside: true });
  }
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({ date: new Date(viewYear, viewMonth, d), outside: false });
  }
  while (cells.length < 42) {
    const last = cells[cells.length - 1].date;
    cells.push({ date: new Date(last.getFullYear(), last.getMonth(), last.getDate() + 1), outside: true });
  }

  const yearOptions = React.useMemo(() => {
    const base = today.getFullYear();
    const years: number[] = [];
    for (let y = base - 15; y <= base + 5; y++) years.push(y);
    return years;
  }, [today]);

  const goMonth = (delta: number) => {
    const next = new Date(viewYear, viewMonth + delta, 1);
    setViewYear(next.getFullYear());
    setViewMonth(next.getMonth());
  };

  const minTime = minDate ? stripTime(minDate) : undefined;
  const maxTime = maxDate ? stripTime(maxDate) : undefined;

  return (
    <div className={cn('w-72 p-3', className)}>
      <div className="mb-3 flex items-center gap-1.5">
        <Button
          type="button" variant="outline" size="icon" className="h-7 w-7 shrink-0"
          onClick={() => goMonth(-1)} aria-label="Previous month"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <Select value={String(viewMonth)} onValueChange={(v) => setViewMonth(Number(v))}>
          <SelectTrigger className="h-7 flex-1 text-sm"><SelectValue /></SelectTrigger>
          <SelectContent>
            {MONTHS.map((m, i) => <SelectItem key={m} value={String(i)}>{m}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={String(viewYear)} onValueChange={(v) => setViewYear(Number(v))}>
          <SelectTrigger className="h-7 w-[5.25rem] text-sm"><SelectValue /></SelectTrigger>
          <SelectContent>
            {yearOptions.map((y) => <SelectItem key={y} value={String(y)}>{y}</SelectItem>)}
          </SelectContent>
        </Select>
        <Button
          type="button" variant="outline" size="icon" className="h-7 w-7 shrink-0"
          onClick={() => goMonth(1)} aria-label="Next month"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted-foreground">
        {WEEKDAYS.map((w) => <div key={w} className="py-1 font-medium">{w}</div>)}
      </div>
      <div className="grid grid-cols-7 gap-1">
        {cells.map(({ date, outside }, idx) => {
          const time = stripTime(date);
          const disabled = (minTime !== undefined && time < minTime) || (maxTime !== undefined && time > maxTime);
          const isSelected = selected ? isSameDay(date, selected) : false;
          const isToday = isSameDay(date, today);
          return (
            <button
              key={idx}
              type="button"
              disabled={disabled}
              onClick={() => onSelect(date)}
              className={cn(
                'h-8 w-8 rounded-md text-sm transition-colors hover:bg-accent hover:text-accent-foreground disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent',
                outside && 'text-muted-foreground/50',
                isToday && !isSelected && 'border border-primary/50',
                isSelected && 'bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground',
              )}
            >
              {date.getDate()}
            </button>
          );
        })}
      </div>
    </div>
  );
}
