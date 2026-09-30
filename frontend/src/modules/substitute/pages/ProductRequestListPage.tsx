import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Loader2, PackageSearch, Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent } from '@/shared/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { cn } from '@/shared/lib/utils';
import { ApiError, type PageResponse } from '@/shared/types/api';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { asFeatureNotAvailable } from '@/modules/subscription/lib/featureNotAvailable';
import { UpgradeDialog } from '@/modules/subscription/components/UpgradeDialog';
import type { FeatureNotAvailableDetails } from '@/modules/subscription/lib/featureNotAvailable';
import { substituteService } from '../services/substituteService';
import { NewProductRequestDialog } from '../components/NewProductRequestDialog';
import { SUBSTITUTE_ROUTES } from '../constants';
import type { ProductRequestResponse, ProductRequestStatus } from '../types';

const STATUS_FILTERS: { value: ProductRequestStatus | null; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: null, label: 'All' },
];

const STATUS_TONE: Record<ProductRequestStatus, string> = {
  OPEN: 'bg-warning/10 text-warning',
  RESOLVED: 'bg-success/10 text-success',
  CANCELLED: 'bg-muted text-muted-foreground',
};

/** CR-089 §11. The queue of customer product requests, newest first. */
export function ProductRequestListPage() {
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const canManage = hasPermission(PERMISSIONS.PRODUCT_REQUEST_MANAGE);
  const [status, setStatus] = useState<ProductRequestStatus | null>('OPEN');
  const [page, setPage] = useState<PageResponse<ProductRequestResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [upgrade, setUpgrade] = useState<FeatureNotAvailableDetails | null>(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    substituteService.search(status)
      .then(setPage)
      .catch((caught) => {
        const details = asFeatureNotAvailable(caught);
        if (details) {
          setUpgrade(details);
          setPage({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
          return;
        }
        setError(caught instanceof ApiError
          ? caught
          : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
      })
      .finally(() => setLoading(false));
  }, [status]);

  useEffect(load, [load]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Product requests"
        description="What customers asked for that was out of stock, and the in-stock alternatives suggested from your own catalogue."
        actions={canManage ? (
          <div>
            <Button onClick={() => setCreating(true)} data-testid="new-product-request">
              <Plus className="mr-2 h-4 w-4" /> New request
            </Button>
          </div>
        ) : undefined}
      />

      <div className="flex flex-wrap gap-2">
        {STATUS_FILTERS.map((filter) => (
          <Button key={filter.label} size="sm" variant={filter.value === status ? 'default' : 'outline'}
                  onClick={() => setStatus(filter.value)}>
            {filter.label}
          </Button>
        ))}
      </div>

      {error ? (
        <Card><ErrorState error={error} onRetry={load} /></Card>
      ) : loading || !page ? (
        <div className="flex justify-center py-16">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
        </div>
      ) : page.content.length === 0 ? (
        <Card>
          <CardContent className="py-10">
            <EmptyState
              icon={PackageSearch}
              title={upgrade ? 'Smart Substitute is a Premium feature' : 'No product requests yet'}
              description={upgrade
                ? `"${upgrade.featureName}" is available in the ${upgrade.requiredPlanName ?? 'Premium'} plan.`
                : 'When a customer asks for something you are out of, record it here to see what you could offer instead.'}
              action={canManage && !upgrade ? (
                <Button onClick={() => setCreating(true)}><Plus className="mr-2 h-4 w-4" /> New request</Button>
              ) : undefined}
            />
          </CardContent>
        </Card>
      ) : (
        <Card>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Requested</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Stock</TableHead>
                  <TableHead>Alternatives</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>When</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {page.content.map((request) => (
                  <TableRow key={request.id} className="cursor-pointer"
                            onClick={() => navigate(SUBSTITUTE_ROUTES.detail(request.id))}>
                    <TableCell>
                      <Link to={SUBSTITUTE_ROUTES.detail(request.id)} className="font-medium hover:underline"
                            onClick={(e) => e.stopPropagation()}>
                        {request.requestedProduct.productName}
                      </Link>
                      <div className="text-xs text-muted-foreground">{request.requestedProduct.productCode}</div>
                    </TableCell>
                    <TableCell className="tabular">{request.requestedQuantity}</TableCell>
                    <TableCell className={cn('tabular', request.requestedProductStock <= 0 && 'text-destructive')}>
                      {request.requestedProductStock}
                    </TableCell>
                    <TableCell className="tabular">
                      {request.selectedProduct
                        ? <span className="text-success">{request.selectedProduct.productName}</span>
                        : request.suggestions.length}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{request.customerName ?? '—'}</TableCell>
                    <TableCell>
                      <Badge className={cn('font-normal', STATUS_TONE[request.status])}>{request.status}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(request.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Card>
      )}

      <NewProductRequestDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(created) => {
          setCreating(false);
          navigate(SUBSTITUTE_ROUTES.detail(created.id));
        }}
      />
      <UpgradeDialog details={upgrade} onClose={() => setUpgrade(null)} />
    </div>
  );
}
