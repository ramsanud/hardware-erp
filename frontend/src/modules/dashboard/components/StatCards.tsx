import type { ComponentType, SVGProps } from 'react';
import { Link } from 'react-router-dom';
import { ArrowDown, ArrowRight, ArrowUp } from 'lucide-react';
import { Card, CardContent } from '@/shared/components/ui/card';
import { cn } from '@/shared/lib/utils';
import { Sparkline } from './Sparkline';

type Icon = ComponentType<SVGProps<SVGSVGElement>>;
type Tone = 'primary' | 'warning';

const TILE: Record<Tone, string> = {
  primary: 'bg-primary/10 text-primary',
  warning: 'bg-warning/10 text-warning',
};

/**
 * CR-082. A week-over-week (or day-over-day) comparison for a KPI card.
 *
 * `percent` is the measured change. `before` and `now` let the row say the
 * true thing when a percentage cannot: a previous window of zero and a
 * non-zero now is "New", not "0%" and not infinity; both zero is "0%", which
 * for a shop that has not sold anything yet is exactly right.
 */
export interface Delta {
  now: number;
  before: number;
  /** "vs last week", "vs yesterday". */
  against: string;
  /**
   * Whether up is good. Sales up is good; receivables up is not. Decides the
   * colour, so the same arrow can be green on one card and red on the next.
   */
  upIsGood?: boolean;
}

/** The mockup's per-card line colour: green for money in, red for money owed, amber for stock. */
export type SparkTone = 'success' | 'destructive' | 'warning';

interface KpiCardProps {
  label: string;
  value: string;
  icon: Icon;
  tone?: Tone;
  /** Omit when no comparison window exists at all; the row then reads "→ 0% vs …". */
  delta?: Delta;
  /** Text after the arrow when `delta` is omitted, e.g. "vs last week". */
  against?: string;
  /**
   * The series behind the sparkline. Fewer than two points draws the flat
   * baseline - the card always shows its mini chart, as the owner asked.
   */
  series?: number[];
  sparkTone: SparkTone;
  to?: string;
  className?: string;
}

/**
 * Row 1 of the dashboard: icon tile, label, big figure, delta line - and the
 * sparkline pinned top-right beside the label, where the mockup puts it and
 * where there is room. Beside the figure it clipped ("₹26,100.00" at 24px
 * cannot share 226px with an 80px line); beside the delta it overflowed the
 * card. The label is the one short row, so that is the row it shares.
 */
export function KpiCard({
  label, value, icon: Icon, tone = 'primary', delta, against, series = [], sparkTone, to, className,
}: KpiCardProps) {
  const upIsGood = delta?.upIsGood ?? true;
  let direction: 'up' | 'down' | 'flat' = 'flat';
  let figure = '0%';
  if (delta) {
    if (delta.before > 0) {
      const pct = ((delta.now - delta.before) / delta.before) * 100;
      direction = pct > 0 ? 'up' : pct < 0 ? 'down' : 'flat';
      figure = `${Math.abs(pct).toFixed(Math.abs(pct) % 1 === 0 ? 0 : 1)}%`;
    } else if (delta.now > 0) {
      direction = 'up';
      figure = 'New';
    }
  }
  const good = direction === 'flat' ? null : (direction === 'up') === upIsGood;
  const deltaTone = good === null ? 'text-muted-foreground' : good ? 'text-success' : 'text-destructive';
  const DeltaIcon = direction === 'up' ? ArrowUp : direction === 'down' ? ArrowDown : ArrowRight;

  const body = (
    <CardContent className="relative flex h-full items-start gap-3.5 p-5">
      <Sparkline values={series} tone={sparkTone} className="absolute right-5 top-5 h-6 w-16" />
      <span className={cn('mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full', TILE[tone])}>
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0 flex-1">
        <p className="pr-16 text-sm leading-snug text-muted-foreground">{label}</p>
        <p className="figure mt-1 text-2xl font-bold leading-tight">{value}</p>
        <div className="mt-1.5 flex items-end justify-between gap-3">
          {/* Inline, not flex: a flex row drops the space between the figure
              and its caption from the DOM text, so a screen reader heard
              "30.8%vs yesterday". A real space keeps it a sentence. */}
          <p className={cn('whitespace-nowrap text-xs', deltaTone)}>
            <DeltaIcon className="mr-1 inline h-3 w-3 align-[-2px]" aria-hidden />
            <span className="font-semibold">{figure}</span>
            {' '}
            <span className="text-muted-foreground">{delta?.against ?? against ?? 'vs last week'}</span>
          </p>
        </div>
      </div>
    </CardContent>
  );

  const card = <Card className={cn('h-full', to && 'transition-colors hover:bg-accent/40', className)}>{body}</Card>;
  return to ? <Link to={to} className="block h-full">{card}</Link> : card;
}

interface CountCardProps {
  label: string;
  value: number | null;
  icon: Icon;
  tone?: Tone;
  to: string;
  className?: string;
}

/** Row 2: a count and a "View all" link. The whole card is the link. */
export function CountCard({ label, value, icon: Icon, tone = 'primary', to, className }: CountCardProps) {
  return (
    <Link to={to} className={cn('block h-full', className)}>
      <Card className="h-full transition-colors hover:bg-accent/40">
        <CardContent className="flex h-full items-center justify-between gap-3 p-5">
          <div className="flex min-w-0 items-center gap-3.5">
            <span className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-full', TILE[tone])}>
              <Icon className="h-[18px] w-[18px]" aria-hidden />
            </span>
            <div className="min-w-0">
              <p className="text-sm text-muted-foreground">{label}</p>
              <p className="figure mt-0.5 text-2xl font-bold leading-tight">{value ?? '—'}</p>
            </div>
          </div>
          <span className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground">
            View all <ArrowRight className="h-3 w-3" aria-hidden />
          </span>
        </CardContent>
      </Card>
    </Link>
  );
}
