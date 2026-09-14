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
 * `percent` null means there is no honest comparison - the previous window
 * was zero, or no series exists for this figure - and the row says so in
 * words rather than printing "0%" as if it were measured.
 */
export interface Delta {
  percent: number | null;
  /** "vs last week", "vs yesterday". */
  against: string;
  /**
   * Whether up is good. Sales up is good; receivables up is not. Decides the
   * colour, so the same arrow can be green on one card and red on the next.
   */
  upIsGood?: boolean;
}

interface KpiCardProps {
  label: string;
  value: string;
  icon: Icon;
  tone?: Tone;
  delta?: Delta;
  /** Real series only - see Sparkline. Omit when no time series exists for this figure. */
  series?: number[];
  to?: string;
  className?: string;
}

/**
 * Row 1 of the dashboard: label, big figure, a delta line, and a sparkline
 * hard right. Icon tile top-left of the text block, as drawn.
 */
export function KpiCard({ label, value, icon: Icon, tone = 'primary', delta, series, to, className }: KpiCardProps) {
  const percent = delta?.percent ?? null;
  const upIsGood = delta?.upIsGood ?? true;
  const direction = percent === null ? 'flat' : percent > 0 ? 'up' : percent < 0 ? 'down' : 'flat';
  const good = direction === 'flat' ? null : (direction === 'up') === upIsGood;
  const deltaTone = good === null ? 'text-muted-foreground' : good ? 'text-success' : 'text-destructive';
  const sparkTone = series && series.length >= 2
    ? (good === null ? 'muted' : good ? 'success' : 'destructive')
    : 'muted';
  const DeltaIcon = direction === 'up' ? ArrowUp : direction === 'down' ? ArrowDown : ArrowRight;

  /*
   * Icon left, then a text block that owns the full remaining width: the
   * figure is the one thing on this card that must never truncate, and
   * "₹26,100.00" at 24px needs ~120px that a sparkline beside it took away
   * at 1440 (four cards, 226px each). The sparkline sits on the delta row
   * instead - same corner the mockup puts it in, one line lower.
   */
  const body = (
    <CardContent className="flex h-full items-start gap-3.5 p-5">
      <span className={cn('mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full', TILE[tone])}>
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm text-muted-foreground">{label}</p>
        <p className="figure mt-1 text-2xl font-bold leading-tight">{value}</p>
        {delta || (series && series.length >= 2) ? (
          <div className="mt-1.5 flex items-end justify-between gap-3">
            {/* The delta is inline, not flex: a flex row drops the space between
                the figure and its caption from the DOM text, so a screen reader
                heard "30.8%vs yesterday". A real space keeps it a sentence. */}
            {delta ? (
              <p className={cn('whitespace-nowrap text-xs', deltaTone)}>
                <DeltaIcon className="mr-1 inline h-3 w-3 align-[-2px]" aria-hidden />
                <span className="font-semibold">
                  {percent === null ? '—' : `${Math.abs(percent).toFixed(percent % 1 === 0 ? 0 : 1)}%`}
                </span>
                {' '}
                <span className="text-muted-foreground">{delta.against}</span>
              </p>
            ) : <span />}
            {series && series.length >= 2 ? (
              <Sparkline values={series} tone={sparkTone} width={56} height={20} className="shrink-0" />
            ) : null}
          </div>
        ) : null}
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
