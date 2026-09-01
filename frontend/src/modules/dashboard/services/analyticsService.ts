import { apiGet } from '@/services/apiClient';

/**
 * Backend: analytics/controller/AnalyticsController.java (CR-048).
 *
 * Every money value arrives TWICE - raw paise to plot, and a preformatted
 * display string for axes and tooltips. The frontend never formats money
 * itself: Indian digit grouping (1,50,000 rather than 150,000) lives in
 * IndianCurrencyFormat on the server and must not be reimplemented here.
 *
 * Each response also carries a `summary` sentence built server-side. That is
 * the accessible alternative to the chart - it states the conclusion a
 * sighted reader draws from the line's shape, not a list of coordinates.
 */

export type Granularity = 'day' | 'week' | 'month';

export interface Period {
  from: string;
  to: string;
  granularity: string;
}

export interface TrendPoint {
  /** ISO date - the start of the day/week/month bucket. */
  bucket: string;
  revenuePaise: number;
  revenueDisplay: string;
  invoiceCount: number;
}

export interface TrendSeries {
  period: Period;
  points: TrendPoint[];
  summary: string;
}

export interface CategorySlice {
  label: string;
  amountPaise: number;
  amountDisplay: string;
  quantity: number;
  sharePercent: number;
}

export interface CategoryBreakdown {
  period: Period;
  slices: CategorySlice[];
  summary: string;
}

export interface AnalyticsSummary {
  period: Period;
  revenuePaise: number;
  revenueDisplay: string;
  invoiceCount: number;
  averageOrderValuePaise: number;
  averageOrderValueDisplay: string;
  outstandingPaise: number;
  outstandingDisplay: string;
}

const range = (from: string, to: string) => `from=${from}&to=${to}`;

export const analyticsService = {
  summary: (from: string, to: string) =>
    apiGet<AnalyticsSummary>(`/v1/analytics/summary?${range(from, to)}`),

  revenueTrend: (from: string, to: string, granularity: Granularity) =>
    apiGet<TrendSeries>(
      `/v1/analytics/revenue-trend?${range(from, to)}&granularity=${granularity}`,
    ),

  salesByCategory: (from: string, to: string) =>
    apiGet<CategoryBreakdown>(`/v1/analytics/sales-by-category?${range(from, to)}`),
};

// ---------------------------------------------------------------------------
// Period presets
// ---------------------------------------------------------------------------

export type PeriodPresetId = 'week' | 'month' | 'year' | 'all';

export interface PeriodPreset {
  id: PeriodPresetId;
  label: string;
  /** Bucket width. A year of daily points is unreadable; a week of monthly ones is one bar. */
  granularity: Granularity;
  /** Days back from today, or null for "everything". */
  days: number | null;
}

export const PERIOD_PRESETS: PeriodPreset[] = [
  { id: 'week', label: 'Week', granularity: 'day', days: 6 },
  { id: 'month', label: 'Month', granularity: 'day', days: 29 },
  { id: 'year', label: 'Year', granularity: 'month', days: 364 },
  { id: 'all', label: 'All time', granularity: 'month', days: null },
];

const iso = (d: Date) => d.toISOString().slice(0, 10);

/**
 * Resolves a preset to the concrete dates the API needs.
 *
 * "All time" is capped at five years because the backend rejects anything
 * wider - an owner asking for a decade of daily points wants a report, not a
 * chart.
 */
export function resolvePeriod(preset: PeriodPreset): { from: string; to: string } {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - (preset.days ?? 365 * 5 - 1));
  return { from: iso(from), to: iso(to) };
}
