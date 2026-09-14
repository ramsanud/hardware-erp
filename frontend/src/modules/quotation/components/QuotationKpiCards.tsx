import type { ComponentType, SVGProps } from 'react';
import { CheckCircle2, Clock, FileSpreadsheet, XCircle } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent } from '@/shared/components/ui/card';
import { Skeleton } from '@/shared/components/ui/skeleton';
import { cn } from '@/shared/lib/utils';
import type { QuotationStatsResponse } from '../types';

type Icon = ComponentType<SVGProps<SVGSVGElement>>;
type Tone = 'primary' | 'warning' | 'success' | 'destructive';

const TILE: Record<Tone, string> = {
  primary: 'bg-primary/10 text-primary',
  warning: 'bg-warning/10 text-warning',
  success: 'bg-success/10 text-success',
  destructive: 'bg-destructive/10 text-destructive',
};

interface CardSpec {
  key: keyof Pick<QuotationStatsResponse, 'totalCount' | 'pendingCount' | 'approvedCount' | 'closedCount'>;
  valueKey: keyof Pick<QuotationStatsResponse, 'totalValueDisplay' | 'pendingValueDisplay' | 'approvedValueDisplay' | 'closedValueDisplay'>;
  label: string;
  badge?: string;
  tone: Tone;
  icon: Icon;
}

/**
 * CR-083. The four cards above the filter bar. The figures come from
 * /v1/quotations/stats - every quotation matching the search and date
 * range, never the 20 on screen - so "Total" is the shop's real number.
 *
 * Amber / emerald / rose in the brief are the warning / success /
 * destructive tokens, the same three every status badge already uses, so
 * the card and the badge for the same state are the same colour.
 */
const CARDS: CardSpec[] = [
  { key: 'totalCount', valueKey: 'totalValueDisplay', label: 'Total quotations', tone: 'primary', icon: FileSpreadsheet },
  { key: 'pendingCount', valueKey: 'pendingValueDisplay', label: 'Drafts / Pending', badge: 'Pending', tone: 'warning', icon: Clock },
  { key: 'approvedCount', valueKey: 'approvedValueDisplay', label: 'Approved / Converted', badge: 'Approved', tone: 'success', icon: CheckCircle2 },
  { key: 'closedCount', valueKey: 'closedValueDisplay', label: 'Expired / Rejected', badge: 'Closed', tone: 'destructive', icon: XCircle },
];

interface QuotationKpiCardsProps {
  stats: QuotationStatsResponse | null;
  loading: boolean;
}

export function QuotationKpiCards({ stats, loading }: QuotationKpiCardsProps) {
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4" data-testid="quotation-kpis">
      {CARDS.map(({ key, valueKey, label, badge, tone, icon: Icon }) => (
        <Card key={key} data-testid={`kpi-${key}`}>
          <CardContent className="flex flex-col gap-3 p-4">
            <div className="flex items-start justify-between gap-2">
              <span className={cn('inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', TILE[tone])}>
                <Icon className="h-4 w-4" aria-hidden />
              </span>
              {badge ? (
                <Badge variant={tone === 'primary' ? 'default' : tone} className="hidden sm:inline-flex">{badge}</Badge>
              ) : null}
            </div>
            <div className="min-w-0">
              <p className="truncate text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
              {loading && !stats ? (
                <>
                  <Skeleton className="mt-1.5 h-7 w-12" />
                  <Skeleton className="mt-1.5 h-4 w-24" />
                </>
              ) : (
                <>
                  <p className="tabular mt-0.5 text-2xl font-semibold leading-tight" data-testid={`kpi-${key}-count`}>
                    {stats ? stats[key] : '—'}
                  </p>
                  <p className="tabular mt-0.5 truncate text-sm text-muted-foreground">
                    {stats ? `₹${stats[valueKey]}` : '—'}
                  </p>
                </>
              )}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
