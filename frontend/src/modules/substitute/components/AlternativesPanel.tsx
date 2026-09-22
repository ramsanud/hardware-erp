import { useState } from 'react';
import { ArrowLeftRight, Check, Loader2, PackageX, RefreshCw, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/components/ui/table';
import { cn } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { substituteService } from '../services/substituteService';
import type { ComparisonResponse, MatchLevel, ProductRequestResponse, SuggestionResponse } from '../types';

interface AlternativesPanelProps {
  request: ProductRequestResponse;
  onChanged: (request: ProductRequestResponse) => void;
}

const LEVEL_LABEL: Record<MatchLevel, string> = {
  EXCELLENT: 'Excellent match',
  HIGH: 'High match',
  MEDIUM: 'Medium match',
  LOW: 'Low match',
  DO_NOT_RECOMMEND: 'Not recommended',
};

const LEVEL_TONE: Record<MatchLevel, string> = {
  EXCELLENT: 'bg-success/10 text-success',
  HIGH: 'bg-primary/10 text-primary',
  MEDIUM: 'bg-warning/10 text-warning',
  LOW: 'bg-muted text-muted-foreground',
  DO_NOT_RECOMMEND: 'bg-destructive/10 text-destructive',
};

/**
 * CR-089 §11-§15. "Product unavailable" then "Smart alternatives" - top N
 * as stored, each with its score out of the real maximum, its band and the
 * engine's own reason. The owner picks; nothing is auto-substituted and
 * no invoice is touched (§13/§24).
 */
export function AlternativesPanel({ request, onChanged }: AlternativesPanelProps) {
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canManage = hasPermission(PERMISSIONS.PRODUCT_REQUEST_MANAGE);
  const [showAll, setShowAll] = useState(false);
  const [selecting, setSelecting] = useState<number | null>(null);
  const [recomputing, setRecomputing] = useState(false);
  const [comparison, setComparison] = useState<ComparisonResponse | null>(null);
  const [comparing, setComparing] = useState<number | null>(null);

  const isOpen = request.status === 'OPEN';
  const visible = showAll ? request.suggestions : request.suggestions.slice(0, 3);

  const select = async (suggestion: SuggestionResponse) => {
    setSelecting(suggestion.product.id);
    try {
      const updated = await substituteService.selectAlternative(request.id, suggestion.product.id);
      toast.success(`Recorded: offer ${suggestion.product.productName}. Continue billing as usual.`);
      onChanged(updated);
    } catch (caught) {
      toast.error(caught, 'Could not record the selection.');
    } finally {
      setSelecting(null);
    }
  };

  const recompute = async () => {
    setRecomputing(true);
    try {
      onChanged(await substituteService.recompute(request.id));
      toast.success('Alternatives recalculated against current stock and prices.');
    } catch (caught) {
      toast.error(caught, 'Could not recalculate.');
    } finally {
      setRecomputing(false);
    }
  };

  const compare = async (suggestion: SuggestionResponse) => {
    setComparing(suggestion.product.id);
    try {
      setComparison(await substituteService.compare(request.id, suggestion.product.id));
    } catch (caught) {
      toast.error(caught, 'Could not load the comparison.');
    } finally {
      setComparing(null);
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex flex-row items-start gap-3 space-y-0">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-destructive/10">
            <PackageX className="h-5 w-5 text-destructive" />
          </div>
          <div className="min-w-0 flex-1">
            <CardDescription>Product unavailable</CardDescription>
            <CardTitle className="truncate text-lg">{request.requestedProduct.productName}</CardTitle>
            <dl className="mt-2 grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-4">
              <div><dt className="text-muted-foreground">Requested</dt><dd className="font-medium tabular">{request.requestedQuantity} {request.requestedProduct.unit}</dd></div>
              <div><dt className="text-muted-foreground">Current stock</dt><dd className={cn('font-medium tabular', request.requestedProductStock <= 0 && 'text-destructive')}>{request.requestedProductStock}</dd></div>
              {request.requestedBudgetDisplay ? (
                <div><dt className="text-muted-foreground">Budget</dt><dd className="font-medium">₹{request.requestedBudgetDisplay}</dd></div>
              ) : null}
              {request.customerName ? (
                <div><dt className="text-muted-foreground">Customer</dt><dd className="truncate font-medium">{request.customerName}</dd></div>
              ) : null}
            </dl>
          </div>
        </CardHeader>
      </Card>

      {request.selectedProduct ? (
        <Card className="border-success/40">
          <CardHeader className="flex flex-row items-center gap-3 space-y-0">
            <Check className="h-5 w-5 shrink-0 text-success" />
            <div>
              <CardDescription>Selected alternative</CardDescription>
              <CardTitle className="text-base">{request.selectedProduct.productName}</CardTitle>
              <p className="mt-1 text-sm text-muted-foreground">
                Bill whatever was actually sold - recording this choice changes no invoice.
              </p>
            </div>
          </CardHeader>
        </Card>
      ) : null}

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 space-y-0">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <Sparkles className="h-4 w-4 text-primary" /> Smart alternatives
            </CardTitle>
            <CardDescription>
              These products may be suitable alternatives - the decision is yours. Only in-stock products from your own catalogue are shown.
            </CardDescription>
          </div>
          {isOpen && canManage ? (
            <Button variant="outline" size="sm" onClick={recompute} disabled={recomputing}>
              {recomputing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
              Recalculate
            </Button>
          ) : null}
        </CardHeader>
        <CardContent className="space-y-3">
          {request.suggestions.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">
              No in-stock product in your catalogue scored above your minimum. Add an alternative mapping on the product, or lower the threshold in Substitute settings.
            </p>
          ) : visible.map((suggestion, index) => (
            <div key={suggestion.product.id}
                 className={cn('rounded-lg border border-border p-4', index === 0 && isOpen && 'border-primary/40 bg-primary/5')}
                 data-suggestion-rank={index + 1}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{index + 1}. {suggestion.product.productName}</span>
                    <Badge className={cn('font-normal', LEVEL_TONE[suggestion.matchLevel])}>{LEVEL_LABEL[suggestion.matchLevel]}</Badge>
                    {suggestion.source === 'MANUAL_MAPPING' ? (
                      <Badge variant="outline" className="font-normal">Your mapping</Badge>
                    ) : null}
                    {suggestion.aboveBudget ? (
                      <Badge className="bg-warning/10 font-normal text-warning">Above budget</Badge>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-muted-foreground">{suggestion.reason}</p>
                  <dl className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm">
                    <div><dt className="inline text-muted-foreground">Code </dt><dd className="inline font-medium">{suggestion.product.productCode}</dd></div>
                    {suggestion.product.brandName ? <div><dt className="inline text-muted-foreground">Brand </dt><dd className="inline font-medium">{suggestion.product.brandName}</dd></div> : null}
                    {suggestion.product.sizeLabel ? <div><dt className="inline text-muted-foreground">Size </dt><dd className="inline font-medium">{suggestion.product.sizeLabel}</dd></div> : null}
                    {suggestion.product.material ? <div><dt className="inline text-muted-foreground">Material </dt><dd className="inline font-medium">{suggestion.product.material}</dd></div> : null}
                    <div><dt className="inline text-muted-foreground">Stock </dt><dd className="inline font-medium tabular">{suggestion.product.availableStock} {suggestion.product.unit}</dd></div>
                    <div><dt className="inline text-muted-foreground">Price </dt><dd className="inline font-medium">₹{suggestion.product.sellingPriceDisplay}</dd></div>
                    <div><dt className="inline text-muted-foreground">Score </dt><dd className="inline font-medium tabular">{suggestion.score}/{suggestion.maximumScore}</dd></div>
                  </dl>
                </div>
                <div className="flex shrink-0 flex-wrap gap-2">
                  <Button variant="outline" size="sm" asChild>
                    <Link to={`/products/${suggestion.product.id}`}>View</Link>
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => compare(suggestion)}
                          disabled={comparing === suggestion.product.id}>
                    {comparing === suggestion.product.id
                      ? <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      : <ArrowLeftRight className="mr-2 h-4 w-4" />}
                    Compare
                  </Button>
                  {isOpen && canManage ? (
                    <Button size="sm" onClick={() => select(suggestion)} disabled={selecting !== null}>
                      {selecting === suggestion.product.id ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                      Select
                    </Button>
                  ) : null}
                </div>
              </div>
            </div>
          ))}
          {request.suggestions.length > 3 ? (
            <Button variant="ghost" size="sm" onClick={() => setShowAll((value) => !value)}>
              {showAll ? 'Show top 3 only' : `Show all ${request.suggestions.length}`}
            </Button>
          ) : null}
        </CardContent>
      </Card>

      <Dialog open={comparison !== null} onOpenChange={(open) => !open && setComparison(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Compare</DialogTitle>
            <DialogDescription>Requested product against the alternative, attribute by attribute.</DialogDescription>
          </DialogHeader>
          {comparison ? (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Feature</TableHead>
                    <TableHead>Requested</TableHead>
                    <TableHead>Alternative</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {comparison.rows.map((row) => (
                    <TableRow key={row.label}>
                      <TableCell className="text-muted-foreground">{row.label}</TableCell>
                      <TableCell>{row.requestedValue}</TableCell>
                      <TableCell className={cn(row.same ? 'text-success' : 'font-medium')}>
                        {row.alternativeValue}{row.same ? ' ✓' : ''}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
