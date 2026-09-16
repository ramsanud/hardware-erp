import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Loader2, XCircle } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { substituteService } from '../services/substituteService';
import { AlternativesPanel } from '../components/AlternativesPanel';
import { NearbyAvailabilityPanel } from '@/modules/discovery/components/NearbyAvailabilityPanel';
import { SUBSTITUTE_ROUTES } from '../constants';
import type { ProductRequestResponse } from '../types';

/** CR-089. One request: the unavailable card, the alternatives, compare and select. */
export function ProductRequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canManage = hasPermission(PERMISSIONS.PRODUCT_REQUEST_MANAGE);
  const [request, setRequest] = useState<ProductRequestResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const load = useCallback(() => {
    if (!id) return;
    setLoading(true);
    setError(null);
    substituteService.get(Number(id))
      .then(setRequest)
      .catch((caught) => setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 })))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(load, [load]);

  const cancel = async () => {
    if (!request) return;
    setCancelling(true);
    try {
      setRequest(await substituteService.cancel(request.id));
      toast.success('Request cancelled.');
    } catch (caught) {
      toast.error(caught, 'Could not cancel the request.');
    } finally {
      setCancelling(false);
    }
  };

  if (error) {
    return (
      <div className="space-y-6">
        <PageHeader title="Product request" />
        <Card><ErrorState error={error} onRetry={load} /></Card>
      </div>
    );
  }
  if (loading || !request) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <PageHeader
        title={request.requestedProduct.productName}
        description={`Requested ${new Date(request.createdAt).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}`}
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" asChild>
              <Link to={SUBSTITUTE_ROUTES.list}><ArrowLeft className="mr-2 h-4 w-4" /> All requests</Link>
            </Button>
            {request.status === 'OPEN' && canManage ? (
              <Button variant="outline" onClick={cancel} disabled={cancelling}>
                {cancelling ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <XCircle className="mr-2 h-4 w-4" />}
                Cancel request
              </Button>
            ) : null}
          </div>
        )}
      />
      <AlternativesPanel request={request} onChanged={setRequest} />
      {/* CR-090 - the owner-only nearby view. Renders nothing at all on a plan without it. */}
      <NearbyAvailabilityPanel requestId={request.id} requestOpen={request.status === 'OPEN'} />
    </div>
  );
}
