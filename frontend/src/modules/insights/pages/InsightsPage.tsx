import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Boxes, Layers, Lightbulb, Link2, Loader2, RefreshCw, Tags, TrendingUp,
} from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  Tabs, TabsContent, TabsList, TabsTrigger,
} from '@/shared/components/ui/tabs';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ApiError } from '@/shared/types/api';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { PERMISSIONS } from '@/modules/auth/constants';
import { PRODUCT_ROUTES } from '@/modules/product/constants';
import { asFeatureNotAvailable } from '@/modules/subscription/lib/featureNotAvailable';
import { UpgradeDialog } from '@/modules/subscription/components/UpgradeDialog';
import type { FeatureNotAvailableDetails } from '@/modules/subscription/lib/featureNotAvailable';
import {
  insightsService, type BoughtTogetherResponse, type DemandTrendResponse, type OverstockResponse,
  type PricingInsightResponse, type ReorderResponse, type SlowMovingResponse,
} from '../services/insightsService';

const WINDOWS = [
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
  { value: '180', label: 'Last 180 days' },
];

const PRICING_FLAG: Record<string, { label: string; variant: 'destructive' | 'warning' | 'info' }> = {
  SELLING_BELOW_COST: { label: 'Below cost', variant: 'destructive' },
  LOW_MARGIN: { label: 'Low margin', variant: 'warning' },
  HEAVILY_DISCOUNTED: { label: 'Heavily discounted', variant: 'info' },
};

function ProductLink({ id, name, code }: { id: number; name: string; code?: string }) {
  return (
    <span>
      <Link to={PRODUCT_ROUTES.detail(id)} className="font-medium text-primary hover:underline">{name}</Link>
      {code ? <span className="block text-xs text-muted-foreground">{code}</span> : null}
    </span>
  );
}

/**
 * CR-092 Smart Insights. Six read-only views, each computed by the server
 * from invoice lines, stock and purchase rows in the chosen window. The
 * summary sentence under each heading is the server's own conclusion; an
 * empty table says why it is empty rather than showing a placeholder.
 */
export function InsightsPage() {
  const { hasPermission } = useAuth();
  const canSeeCost = hasPermission(PERMISSIONS.PRODUCT_VIEW_COST);
  const [days, setDays] = useState('90');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [upgrade, setUpgrade] = useState<FeatureNotAvailableDetails | null>(null);
  const [slow, setSlow] = useState<SlowMovingResponse | null>(null);
  const [overstock, setOverstock] = useState<OverstockResponse | null>(null);
  const [reorder, setReorder] = useState<ReorderResponse | null>(null);
  const [trend, setTrend] = useState<DemandTrendResponse | null>(null);
  const [together, setTogether] = useState<BoughtTogetherResponse | null>(null);
  const [pricing, setPricing] = useState<PricingInsightResponse | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    const d = Number(days);
    try {
      const [s, o, r, t, b] = await Promise.all([
        insightsService.slowMoving(d),
        insightsService.overstock(d, 120),
        insightsService.reorder(Math.min(d, 30), 7),
        insightsService.demandTrend(Math.min(d, 30)),
        insightsService.boughtTogether(d),
      ]);
      setSlow(s); setOverstock(o); setReorder(r); setTrend(t); setTogether(b);
      if (canSeeCost) setPricing(await insightsService.pricing(d));
    } catch (caught) {
      const parsed = asFeatureNotAvailable(caught);
      if (parsed) { setUpgrade(parsed); return; }
      setError(caught instanceof ApiError ? caught : new ApiError({ message: 'Could not load insights', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [days, canSeeCost]);

  useEffect(() => { void reload(); }, [reload]);

  return (
    <>
      <PageHeader
        title="Smart insights"
        description="What your own sales and stock records say: what is not moving, what is running out, what sells together, and where the price is wrong."
        actions={(
          <div className="flex items-center gap-2">
            <Select value={days} onValueChange={setDays}>
              <SelectTrigger className="h-9 w-40"><SelectValue /></SelectTrigger>
              <SelectContent>{WINDOWS.map((w) => <SelectItem key={w.value} value={w.value}>{w.label}</SelectItem>)}</SelectContent>
            </Select>
            <Button type="button" variant="outline" size="sm" onClick={() => void reload()} loading={loading}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
          </div>
        )}
      />

      {error ? <ErrorState error={error} onRetry={() => void reload()} /> : upgrade ? (
        <Card><CardContent className="py-10 text-center text-sm text-muted-foreground">Smart insights are part of a higher plan.</CardContent></Card>
      ) : loading && !slow ? (
        <div className="flex items-center justify-center py-16 text-muted-foreground"><Loader2 className="h-5 w-5 animate-spin" aria-label="Loading" /></div>
      ) : (
        <Tabs defaultValue="reorder">
          <TabsList className="flex-wrap">
            <TabsTrigger value="reorder">Reorder</TabsTrigger>
            <TabsTrigger value="slow">Slow-moving</TabsTrigger>
            <TabsTrigger value="overstock">Overstock</TabsTrigger>
            <TabsTrigger value="trend">Demand trend</TabsTrigger>
            <TabsTrigger value="together">Bought together</TabsTrigger>
            {canSeeCost ? <TabsTrigger value="pricing">Pricing</TabsTrigger> : null}
          </TabsList>

          <TabsContent value="reorder">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base"><Boxes className="h-4 w-4 text-primary" aria-hidden />Reorder suggestions</CardTitle>
                <CardDescription>{reorder?.summary}</CardDescription>
              </CardHeader>
              {reorder && reorder.items.length > 0 ? (
                <CardContent>
                  <Table>
                    <TableHeader><TableRow><TableHead>Product</TableHead><TableHead className="text-right">On hand</TableHead><TableHead className="text-right">Sells / day</TableHead><TableHead className="text-right">Days left</TableHead><TableHead className="text-right">Suggested order</TableHead><TableHead>Why</TableHead></TableRow></TableHeader>
                    <TableBody>
                      {reorder.items.map((i) => (
                        <TableRow key={i.productId}>
                          <TableCell><ProductLink id={i.productId} name={i.productName} code={i.productCode} /></TableCell>
                          <TableCell className="text-right tabular">{i.quantityOnHand} {i.unit}</TableCell>
                          <TableCell className="text-right tabular">{i.averageDailySales}</TableCell>
                          <TableCell className="text-right tabular">{i.daysOfCover ?? '—'}</TableCell>
                          <TableCell className="text-right tabular font-semibold">{i.suggestedQuantity} {i.unit}</TableCell>
                          <TableCell className="text-sm text-muted-foreground">{i.reason}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              ) : null}
            </Card>
          </TabsContent>

          <TabsContent value="slow">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base"><Layers className="h-4 w-4 text-primary" aria-hidden />Slow-moving stock</CardTitle>
                <CardDescription>{slow?.summary}</CardDescription>
              </CardHeader>
              {slow && slow.items.length > 0 ? (
                <CardContent>
                  <Table>
                    <TableHeader><TableRow><TableHead>Product</TableHead><TableHead className="text-right">On hand</TableHead><TableHead>Last sold</TableHead>{canSeeCost ? <TableHead className="text-right">Value at cost</TableHead> : null}</TableRow></TableHeader>
                    <TableBody>
                      {slow.items.map((i) => (
                        <TableRow key={i.productId}>
                          <TableCell><ProductLink id={i.productId} name={i.productName} code={i.productCode} /></TableCell>
                          <TableCell className="text-right tabular">{i.quantityOnHand} {i.unit}</TableCell>
                          <TableCell>{i.lastSoldOn ?? 'Never'}</TableCell>
                          {canSeeCost ? <TableCell className="text-right tabular">₹{i.stockValueDisplay}</TableCell> : null}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              ) : null}
            </Card>
          </TabsContent>

          <TabsContent value="overstock">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base"><Layers className="h-4 w-4 text-primary" aria-hidden />Overstock</CardTitle>
                <CardDescription>{overstock?.summary}</CardDescription>
              </CardHeader>
              {overstock && overstock.items.length > 0 ? (
                <CardContent>
                  <Table>
                    <TableHeader><TableRow><TableHead>Product</TableHead><TableHead className="text-right">On hand</TableHead><TableHead className="text-right">Sells / day</TableHead><TableHead className="text-right">Days of cover</TableHead>{canSeeCost ? <TableHead className="text-right">Value at cost</TableHead> : null}</TableRow></TableHeader>
                    <TableBody>
                      {overstock.items.map((i) => (
                        <TableRow key={i.productId}>
                          <TableCell><ProductLink id={i.productId} name={i.productName} code={i.productCode} /></TableCell>
                          <TableCell className="text-right tabular">{i.quantityOnHand} {i.unit}</TableCell>
                          <TableCell className="text-right tabular">{i.averageDailySales}</TableCell>
                          <TableCell className="text-right tabular">{i.daysOfCover ?? '—'}</TableCell>
                          {canSeeCost ? <TableCell className="text-right tabular">₹{i.stockValueDisplay}</TableCell> : null}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              ) : null}
            </Card>
          </TabsContent>

          <TabsContent value="trend">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base"><TrendingUp className="h-4 w-4 text-primary" aria-hidden />Demand trend</CardTitle>
                <CardDescription>{trend?.summary}{trend ? ` Comparing ${trend.current.from} – ${trend.current.to} with ${trend.previous.from} – ${trend.previous.to}.` : ''}</CardDescription>
              </CardHeader>
              {trend && (trend.rising.length > 0 || trend.falling.length > 0) ? (
                <CardContent className="grid gap-6 lg:grid-cols-2">
                  {[{ title: 'Rising', rows: trend.rising }, { title: 'Falling', rows: trend.falling }].map((group) => (
                    <div key={group.title}>
                      <h3 className="mb-2 text-sm font-medium">{group.title}</h3>
                      {group.rows.length === 0 ? <p className="text-sm text-muted-foreground">None.</p> : (
                        <Table>
                          <TableHeader><TableRow><TableHead>Product</TableHead><TableHead className="text-right">Previous</TableHead><TableHead className="text-right">Current</TableHead><TableHead className="text-right">Change</TableHead></TableRow></TableHeader>
                          <TableBody>
                            {group.rows.map((i) => (
                              <TableRow key={i.productId}>
                                <TableCell><ProductLink id={i.productId} name={i.productName} /></TableCell>
                                <TableCell className="text-right tabular">{i.previousQuantity}</TableCell>
                                <TableCell className="text-right tabular">{i.currentQuantity}</TableCell>
                                <TableCell className="text-right tabular">{i.changePercent == null ? 'new' : `${i.changePercent > 0 ? '+' : ''}${i.changePercent}%`}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      )}
                    </div>
                  ))}
                </CardContent>
              ) : null}
            </Card>
          </TabsContent>

          <TabsContent value="together">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base"><Link2 className="h-4 w-4 text-primary" aria-hidden />Frequently bought together</CardTitle>
                <CardDescription>{together?.summary}</CardDescription>
              </CardHeader>
              {together && together.pairs.length > 0 ? (
                <CardContent>
                  <Table>
                    <TableHeader><TableRow><TableHead>When a customer buys</TableHead><TableHead>They also buy</TableHead><TableHead className="text-right">Together on</TableHead><TableHead className="text-right">Of A&apos;s invoices</TableHead></TableRow></TableHeader>
                    <TableBody>
                      {together.pairs.map((p) => (
                        <TableRow key={`${p.productAId}-${p.productBId}`}>
                          <TableCell><ProductLink id={p.productAId} name={p.productAName} /></TableCell>
                          <TableCell><ProductLink id={p.productBId} name={p.productBName} /></TableCell>
                          <TableCell className="text-right tabular">{p.invoicesTogether} invoice{p.invoicesTogether === 1 ? '' : 's'}</TableCell>
                          <TableCell className="text-right tabular">{p.supportPercent == null ? '—' : `${p.supportPercent}%`}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              ) : null}
            </Card>
          </TabsContent>

          {canSeeCost ? (
            <TabsContent value="pricing">
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-base"><Tags className="h-4 w-4 text-primary" aria-hidden />Pricing</CardTitle>
                  <CardDescription>{pricing?.summary}</CardDescription>
                </CardHeader>
                {pricing && pricing.items.length > 0 ? (
                  <CardContent>
                    <Table>
                      <TableHeader><TableRow><TableHead>Product</TableHead><TableHead>Flag</TableHead><TableHead className="text-right">List price</TableHead><TableHead className="text-right">Avg. cost</TableHead><TableHead className="text-right">Margin</TableHead><TableHead className="text-right">Avg. realised</TableHead></TableRow></TableHeader>
                      <TableBody>
                        {pricing.items.map((i) => (
                          <TableRow key={i.productId}>
                            <TableCell><ProductLink id={i.productId} name={i.productName} code={i.productCode} /></TableCell>
                            <TableCell><Badge variant={PRICING_FLAG[i.flag]?.variant ?? 'secondary'}>{PRICING_FLAG[i.flag]?.label ?? i.flag}</Badge></TableCell>
                            <TableCell className="text-right tabular">₹{i.sellingPriceDisplay}</TableCell>
                            <TableCell className="text-right tabular">₹{i.averageCostDisplay}</TableCell>
                            <TableCell className={`text-right tabular ${i.marginPercent < 0 ? 'text-destructive' : ''}`}>{i.marginPercent}%</TableCell>
                            <TableCell className="text-right tabular">{i.averageRealisedPaise ? `₹${i.averageRealisedDisplay}` : '—'}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </CardContent>
                ) : null}
              </Card>
            </TabsContent>
          ) : null}
        </Tabs>
      )}

      <div className="mt-4 flex items-start gap-2 text-xs text-muted-foreground">
        <Lightbulb className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
        Every figure here is computed from invoices, stock and purchases you recorded. Nothing is estimated or predicted.
      </div>

      <UpgradeDialog details={upgrade} onClose={() => setUpgrade(null)} />
    </>
  );
}
