import { useId } from 'react';

/**
 * CR-082. A mini line with a soft fill under it, for the corner of a KPI card.
 *
 * Plain SVG rather than a Recharts instance: eight of these on one page would
 * mount eight ResponsiveContainers and eight resize observers to draw a line
 * with no axes, no tooltip and no interaction. A polyline is the honest size
 * of the job.
 *
 * Two modes, and the difference is visible in the DOM:
 *
 * - `values` with two or more points draws the series. Shape is the data;
 *   nothing is smoothed or invented.
 * - Fewer than two points draws the **baseline** - a flat line on the
 *   midline. The owner asked for the card to keep its mini chart even at
 *   ₹0.00 rather than leave a hole, and a flat line at the midline is the one
 *   honest picture of "nothing changed": it carries no slope. The element
 *   carries `data-sparkline-empty` so a test (or a reader of the DOM) can tell
 *   a baseline from a measurement.
 *
 * The tone is the caller's call, so a falling receivables line can be red
 * while a falling cost line is green.
 */
interface SparklineProps {
  values: number[];
  tone?: 'success' | 'destructive' | 'warning' | 'muted';
  /** CSS size; the drawing scales to it. Default matches the mockup's w-24 h-10. */
  className?: string;
}

const STROKE: Record<NonNullable<SparklineProps['tone']>, string> = {
  success: 'hsl(var(--success))',
  destructive: 'hsl(var(--destructive))',
  warning: 'hsl(var(--warning))',
  muted: 'hsl(var(--muted-foreground))',
};

const W = 100;
const H = 40;
const PAD = 3;

export function Sparkline({ values, tone = 'success', className = 'h-10 w-24' }: SparklineProps) {
  const gradientId = useId();
  const stroke = STROKE[tone];
  const empty = values.length < 2;

  let line: string;
  if (empty) {
    // A gentle flat curve on the midline: the mockup's zero-state baseline.
    line = `${PAD},${H / 2} ${W / 4},${H / 2 - 1} ${W / 2},${H / 2} ${(3 * W) / 4},${H / 2 - 1} ${W - PAD},${H / 2}`;
  } else {
    const max = Math.max(...values);
    const min = Math.min(...values);
    const span = max - min || 1;
    const step = (W - PAD * 2) / (values.length - 1);
    line = values.map((v, i) => {
      const x = PAD + i * step;
      // A flat series sits on the midline rather than the floor, so "no
      // change" does not read as "bottomed out".
      const y = max === min ? H / 2 : PAD + (H - PAD * 2) * (1 - (v - min) / span);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  }
  const area = `${PAD},${H} ${line} ${W - PAD},${H}`;

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      preserveAspectRatio="none"
      className={className}
      data-sparkline
      data-sparkline-empty={empty ? '' : undefined}
      aria-hidden
      focusable="false"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={stroke} stopOpacity={empty ? 0.1 : 0.22} />
          <stop offset="1" stopColor={stroke} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={area} fill={`url(#${gradientId})`} />
      <polyline
        points={line}
        fill="none"
        stroke={stroke}
        strokeOpacity={empty ? 0.7 : 1}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
