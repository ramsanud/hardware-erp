import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Loader2, MapPin, MessageCircle, Phone, Radar, Store } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Badge } from '@/shared/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { cn } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { SETTINGS_ROUTES } from '@/modules/settings/constants';
import { asFeatureNotAvailable } from '@/modules/subscription/lib/featureNotAvailable';
import { useFeatureGate } from '@/modules/subscription/hooks/useFeatureGate';
import { UpgradeDialog } from '@/modules/subscription/components/UpgradeDialog';
import { discoveryService } from '../services/discoveryService';
import type { NearbyAvailabilityResponse } from '../types';

interface NearbyAvailabilityPanelProps {
  requestId: number;
  /** Only an OPEN request is worth searching for. */
  requestOpen: boolean;
}

/**
 * CR-090. Owner-only. Every field on a shop row is present only because
 * that shop chose to share it; a missing name or phone is not a bug and
 * the copy says so. Call and WhatsApp are plain tel:/wa.me links with no
 * pre-filled text, so nothing about the customer can travel through them.
 */
export function NearbyAvailabilityPanel({ requestId, requestOpen }: NearbyAvailabilityPanelProps) {
  const toast = useToast();
  const gate = useFeatureGate();
  const { hasPermission } = useAuth();
  const canManage = hasPermission(PERMISSIONS.PRODUCT_REQUEST_MANAGE);
  const [nearby, setNearby] = useState<NearbyAvailabilityResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [locked, setLocked] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    discoveryService.nearby(requestId)
      .then(setNearby)
      .catch((caught) => {
        if (asFeatureNotAvailable(caught)) setLocked(true);
      })
      .finally(() => setLoading(false));
  }, [requestId]);

  useEffect(load, [load]);

  const search = async () => {
    setSearching(true);
    try {
      const result = await gate.guard(() => discoveryService.discover(requestId));
      if (result) {
        setNearby(result);
        if (!result.searchUnavailable) {
          toast.success(result.shops.length === 0
            ? 'No participating shop nearby has it right now.'
            : `${result.shops.length} nearby shop${result.shops.length === 1 ? '' : 's'} may have it.`);
        }
      }
    } catch (caught) {
      toast.error(caught, 'Could not search nearby shops.');
    } finally {
      setSearching(false);
    }
  };

  if (locked) {
    return null;
  }

  return (
    <>
      <Card data-testid="nearby-availability">
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 space-y-0">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <Radar className="h-4 w-4 text-primary" /> Nearby availability
            </CardTitle>
            <CardDescription>
              Participating hardware shops near you that may have this product. Only you see this - your customer is never shown another shop.
            </CardDescription>
          </div>
          {requestOpen && canManage ? (
            <Button variant="outline" size="sm" onClick={search} disabled={searching} data-testid="discover-nearby">
              {searching ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Radar className="mr-2 h-4 w-4" />}
              {nearby && nearby.shops.length > 0 ? 'Search again' : 'Search nearby shops'}
            </Button>
          ) : null}
        </CardHeader>
        <CardContent className="space-y-3">
          {loading ? (
            <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
          ) : nearby?.searchUnavailable ? (
            <p className="py-4 text-sm text-muted-foreground">
              {nearby.searchUnavailableReason}{' '}
              <Link to={SETTINGS_ROUTES.shop} className="font-medium text-primary hover:underline">Open Shop settings</Link>
            </p>
          ) : !nearby || nearby.shops.length === 0 ? (
            <p className="py-4 text-center text-sm text-muted-foreground">
              {nearby ? 'No participating shop nearby has it right now.' : 'Not searched yet.'}
            </p>
          ) : nearby.shops.map((shop) => (
            <div key={shop.matchId} className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-border p-4">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <Store className="h-4 w-4 shrink-0 text-muted-foreground" />
                  <span className="font-medium">{shop.shopName ?? 'A nearby hardware shop'}</span>
                  {shop.distanceKm != null ? (
                    <span className="flex items-center gap-1 text-sm text-muted-foreground">
                      <MapPin className="h-3.5 w-3.5" /> about {shop.distanceKm} km
                    </span>
                  ) : null}
                  <Badge className={cn('font-normal', shop.availability === 'AVAILABLE' ? 'bg-success/10 text-success' : 'bg-warning/10 text-warning')}>
                    {shop.availability === 'AVAILABLE' ? 'Available' : 'Likely available'}
                  </Badge>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">
                  Their listing: <span className="text-foreground">{shop.matchedProductName}</span>
                  {!shop.shopName ? ' · shop name not shared' : ''}
                  {!shop.phone ? ' · contact not shared' : ''}
                </p>
              </div>
              {shop.phone ? (
                <div className="flex shrink-0 gap-2">
                  <Button variant="outline" size="sm" asChild>
                    <a href={`tel:${shop.phone}`}><Phone className="mr-2 h-4 w-4" /> Call</a>
                  </Button>
                  {shop.whatsappUrl ? (
                    <Button size="sm" asChild>
                      <a href={shop.whatsappUrl} target="_blank" rel="noopener noreferrer">
                        <MessageCircle className="mr-2 h-4 w-4" /> WhatsApp
                      </a>
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </div>
          ))}
          {nearby && nearby.shops.length > 0 ? (
            <p className="text-xs text-muted-foreground">
              Arrange the product with the other shop yourself. The app sends them nothing - not your customer's name, number or anything else.
            </p>
          ) : null}
        </CardContent>
      </Card>
      <UpgradeDialog details={gate.details} onClose={gate.close} />
    </>
  );
}
