import { useId } from 'react';

/**
 * CR-082. A 60x24 line with a soft fill under it, for the top of a KPI card.
 *
 * Plain SVG rather than a Recharts instance: eight of these on one page would
 * mount eight ResponsiveContainers and eight resize observers to draw a line
 * with no axes, no tooltip and no interaction. A polyline is the honest size
 * of the job.
 *
 * It draws ONLY what it is given. There is no synthetic wiggle for an empty
 * or flat series - a flat line is a flat line, and a series shorter than two
 * points renders nothing. The tone is the caller's call, so a falling
 * receivables line can be red while a falling cost line is green.
 */
interface SparklineProps {
  values: number[];
  tone?: 'success' | 'destructive' | 'warning' | 'muted';
  width?: number;
  height?: number;
  className?: string;
}

const STROKE: Record<NonNullable<SparklineProps['tone']>, string> = {
  success: 'hsl(var(--success))',
  destructive: 'hsl(var(--destructive))',
  warning: 'hsl(var(--warning))',
  muted: 'hsl(var(--muted-foreground))',
};

export function Sparkline({ values, tone = 'success', width = 64, height = 24, className }: SparklineProps) {
  const gradientId = useId();
  if (values.length < 2) return null;

  const max = Math.max(...values);
  const min = Math.min(...values);
  const span = max - min || 1;
  const pad = 2;
  const step = (width - pad * 2) / (values.length - 1);
  const points = values.map((v, i) => {
    const x = pad + i * step;
    // A flat series sits on the midline rather than the floor, so "no change"
    // does not read as "bottomed out".
    const y = max === min ? height / 2 : pad + (height - pad * 2) * (1 - (v - min) / span);
    return [x, y] as const;
  });
  const line = points.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const area = `${pad},${height} ${line} ${(pad + (values.length - 1) * step).toFixed(1)},${height}`;
  const stroke = STROKE[tone];

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      className={className}
      data-sparkline
      aria-hidden
      focusable="false"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={stroke} stopOpacity="0.22" />
          <stop offset="1" stopColor={stroke} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={area} fill={`url(#${gradientId})`} />
      <polyline
        points={line}
        fill="none"
        stroke={stroke}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
