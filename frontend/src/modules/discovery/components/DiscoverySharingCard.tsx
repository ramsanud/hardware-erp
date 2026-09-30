import { useEffect, useState } from 'react';
import { Loader2, LocateFixed, MapPin, Radar, ShieldCheck } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { cn } from '@/shared/lib/utils';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useFeatureGate } from '@/modules/subscription/hooks/useFeatureGate';
import { UpgradeDialog } from '@/modules/subscription/components/UpgradeDialog';
import { discoveryService } from '../services/discoveryService';
import { RADIUS_OPTIONS_KM } from '../constants';
import type { DiscoverySettingRequest } from '../types';

const OFF: DiscoverySettingRequest = {
  discoveryEnabled: false,
  shareShopName: false,
  sharePhone: false,
  shareApproximateLocation: false,
  shareAvailability: false,
  latitude: null,
  longitude: null,
  searchRadiusKm: 5,
};

/**
 * CR-090. The shop's consent, in the owner's own profile settings, with
 * the explanation up front and a double confirmation before anything is
 * shared - the brief's own "clear explanation and double confirmation".
 *
 * Turning discovery ON is the only action that asks twice. Turning it OFF
 * is one click and takes effect on the next search anyone runs - stopping
 * is never harder than starting.
 */
export function DiscoverySharingCard() {
  const toast = useToast();
  const gate = useFeatureGate();
  const { hasPermission } = useAuth();
  const canManage = hasPermission(PERMISSIONS.SETTINGS_MANAGE);
  const [saved, setSaved] = useState<DiscoverySettingRequest>(OFF);
  const [draft, setDraft] = useState<DiscoverySettingRequest>(OFF);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [locating, setLocating] = useState(false);
  const [confirmStep, setConfirmStep] = useState<0 | 1 | 2>(0);
  const [typedConfirmation, setTypedConfirmation] = useState('');

  useEffect(() => {
    discoveryService.settings()
      .then((s) => {
        const value = { ...s };
        setSaved(value);
        setDraft(value);
      })
      .catch(() => { /* Basic plan or no row yet - the defaults (all off) are the truth. */ })
      .finally(() => setLoading(false));
  }, []);

  const dirty = JSON.stringify(draft) !== JSON.stringify(saved);
  const enabling = draft.discoveryEnabled && !saved.discoveryEnabled;
  const hasLocation = draft.latitude !== null && draft.longitude !== null;

  const useMyLocation = () => {
    if (!('geolocation' in navigator)) {
      toast.error(null, 'This browser cannot report a location. Enter the coordinates by hand.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setDraft((d) => ({
          ...d,
          latitude: Number(position.coords.latitude.toFixed(6)),
          longitude: Number(position.coords.longitude.toFixed(6)),
        }));
        setLocating(false);
      },
      () => {
        toast.error(null, 'Location was not shared. Enter the coordinates by hand.');
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  };

  const persist = async () => {
    setSaving(true);
    try {
      const result = await gate.guard(() => discoveryService.updateSettings(draft));
      if (result) {
        const value = { ...result };
        setSaved(value);
        setDraft(value);
        toast.success(result.discoveryEnabled
          ? 'Your shop now takes part in nearby product discovery.'
          : 'Discovery sharing is off. Your shop will not appear in any future search.');
      }
    } catch (caught) {
      toast.error(caught, 'Could not save discovery settings.');
    } finally {
      setSaving(false);
      setConfirmStep(0);
      setTypedConfirmation('');
    }
  };

  const save = () => {
    if (enabling) {
      setConfirmStep(1);
      return;
    }
    void persist();
  };

  const toggle = (key: keyof DiscoverySettingRequest) => (checked: boolean | 'indeterminate') =>
    setDraft((d) => ({ ...d, [key]: checked === true }));

  if (loading) {
    return (
      <Card><CardContent className="flex justify-center py-10">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading" />
      </CardContent></Card>
    );
  }

  return (
    <>
      <Card data-testid="discovery-sharing-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Radar className="h-4 w-4 text-primary" /> Product discovery sharing
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-normal text-primary">Premium</span>
          </CardTitle>
          <CardDescription>
            When another participating hardware shop is out of a product, its owner can see that <em>your</em> shop may
            have it - and only what you tick below. Your customers, prices, quantities, suppliers and sales never leave
            your shop. Nothing is shown to any customer, anywhere. You can turn this off at any time and your shop
            disappears from the very next search.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <Alert>
            <ShieldCheck className="h-4 w-4" />
            <AlertDescription>
              <span className="font-medium">Off by default.</span> Other owners see your shop only while the first box
              is ticked, and each of the other boxes decides one thing they may see. Untick any of them at any time.
            </AlertDescription>
          </Alert>

          <label className={cn('flex items-start gap-3 rounded-md border border-border p-3', draft.discoveryEnabled && 'border-primary/50 bg-primary/5')}>
            <Checkbox checked={draft.discoveryEnabled} onCheckedChange={toggle('discoveryEnabled')} disabled={!canManage} className="mt-0.5" />
            <span>
              <span className="font-medium">Allow my shop to participate in nearby product discovery</span>
              <span className="block text-sm text-muted-foreground">Turns the whole feature on or off. This also lets your own shop search other participating shops when you are out of something.</span>
            </span>
          </label>

          <div className={cn('space-y-3 pl-1', !draft.discoveryEnabled && 'opacity-50')}>
            <label className="flex items-start gap-3">
              <Checkbox checked={draft.shareAvailability} onCheckedChange={toggle('shareAvailability')} disabled={!canManage || !draft.discoveryEnabled} className="mt-0.5" />
              <span><span className="font-medium">Allow product availability status to be shared</span><span className="block text-sm text-muted-foreground">Only "Available" or "Likely available" - never a quantity, never a price. Without this, your shop takes part but is never listed.</span></span>
            </label>
            <label className="flex items-start gap-3">
              <Checkbox checked={draft.shareShopName} onCheckedChange={toggle('shareShopName')} disabled={!canManage || !draft.discoveryEnabled} className="mt-0.5" />
              <span><span className="font-medium">Allow my shop name to be shown to other hardware owners</span><span className="block text-sm text-muted-foreground">Unticked, you appear as an unnamed nearby shop.</span></span>
            </label>
            <label className="flex items-start gap-3">
              <Checkbox checked={draft.sharePhone} onCheckedChange={toggle('sharePhone')} disabled={!canManage || !draft.discoveryEnabled} className="mt-0.5" />
              <span><span className="font-medium">Allow my shop phone number to be shown</span><span className="block text-sm text-muted-foreground">So an owner can call or WhatsApp you. Unticked, they cannot contact you through the app at all.</span></span>
            </label>
            <label className="flex items-start gap-3">
              <Checkbox checked={draft.shareApproximateLocation} onCheckedChange={toggle('shareApproximateLocation')} disabled={!canManage || !draft.discoveryEnabled} className="mt-0.5" />
              <span><span className="font-medium">Allow approximate location to be shown</span><span className="block text-sm text-muted-foreground">Shown as a distance, e.g. "about 2.1 km" - never your address or a pin.</span></span>
            </label>
          </div>

          <div className="space-y-3 rounded-md border border-border p-3">
            <div className="flex items-center gap-2 text-sm font-medium"><MapPin className="h-4 w-4 text-muted-foreground" /> Shop location</div>
            <p className="text-sm text-muted-foreground">
              Needed to find shops near you and to be found by distance. Stored only while you take part; used only for distance, never shown as an address.
            </p>
            <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto]">
              <div className="space-y-1.5">
                <Label htmlFor="disc-lat">Latitude</Label>
                <Input id="disc-lat" type="number" step="0.000001" min="-90" max="90" value={draft.latitude ?? ''} disabled={!canManage}
                       onChange={(e) => setDraft((d) => ({ ...d, latitude: e.target.value === '' ? null : Number(e.target.value) }))} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="disc-lng">Longitude</Label>
                <Input id="disc-lng" type="number" step="0.000001" min="-180" max="180" value={draft.longitude ?? ''} disabled={!canManage}
                       onChange={(e) => setDraft((d) => ({ ...d, longitude: e.target.value === '' ? null : Number(e.target.value) }))} />
              </div>
              <div className="flex items-end">
                <Button type="button" variant="outline" onClick={useMyLocation} disabled={!canManage || locating}>
                  {locating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <LocateFixed className="mr-2 h-4 w-4" />}
                  Use my location
                </Button>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Search radius</Label>
              <div className="flex flex-wrap gap-2">
                {RADIUS_OPTIONS_KM.map((km) => (
                  <Button key={km} type="button" size="sm" variant={draft.searchRadiusKm === km ? 'default' : 'outline'}
                          disabled={!canManage} onClick={() => setDraft((d) => ({ ...d, searchRadiusKm: km }))}>
                    {km} km
                  </Button>
                ))}
              </div>
            </div>
          </div>
        </CardContent>
        {canManage ? (
          <CardFooter className="justify-end gap-2">
            <Button variant="outline" onClick={() => setDraft(saved)} disabled={!dirty || saving}>Discard</Button>
            <Button onClick={save} disabled={!dirty || saving || (draft.discoveryEnabled && !hasLocation)}>
              {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {enabling ? 'Turn on sharing…' : 'Save'}
            </Button>
          </CardFooter>
        ) : null}
      </Card>

      {/* Step 1: what exactly will be shared. Step 2: type ENABLE to confirm. */}
      <Dialog open={confirmStep === 1} onOpenChange={(open) => !open && setConfirmStep(0)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Share your shop with other hardware owners?</DialogTitle>
            <DialogDescription>Here is exactly what other participating owners will be able to see about your shop. Nothing else.</DialogDescription>
          </DialogHeader>
          <ul className="space-y-2 text-sm">
            <li className="flex gap-2"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" /><span>Whether a product they are looking for is <span className="font-medium">available or likely available</span> with you{draft.shareAvailability ? '' : ' - currently unticked, so you will not be listed'}.</span></li>
            <li className="flex gap-2"><ShieldCheck className={cn('mt-0.5 h-4 w-4 shrink-0', draft.shareShopName ? 'text-success' : 'text-muted-foreground')} /><span>Your shop name: <span className="font-medium">{draft.shareShopName ? 'shown' : 'hidden'}</span>.</span></li>
            <li className="flex gap-2"><ShieldCheck className={cn('mt-0.5 h-4 w-4 shrink-0', draft.sharePhone ? 'text-success' : 'text-muted-foreground')} /><span>Your phone number: <span className="font-medium">{draft.sharePhone ? 'shown, with Call / WhatsApp buttons' : 'hidden'}</span>.</span></li>
            <li className="flex gap-2"><ShieldCheck className={cn('mt-0.5 h-4 w-4 shrink-0', draft.shareApproximateLocation ? 'text-success' : 'text-muted-foreground')} /><span>Distance from them: <span className="font-medium">{draft.shareApproximateLocation ? 'shown as "about N km"' : 'hidden'}</span>. Never your address.</span></li>
            <li className="flex gap-2 text-muted-foreground"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" /><span>Never: your prices, quantities, suppliers, customers, invoices, staff or reports.</span></li>
          </ul>
          <DialogFooter className="gap-2 sm:gap-2">
            <Button variant="outline" onClick={() => setConfirmStep(0)}>Not now</Button>
            <Button onClick={() => setConfirmStep(2)}>I understand, continue</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmStep === 2} onOpenChange={(open) => !open && setConfirmStep(0)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Confirm once more</DialogTitle>
            <DialogDescription>Type <span className="font-mono font-medium text-foreground">ENABLE</span> to turn sharing on. You can turn it off again at any time from this card.</DialogDescription>
          </DialogHeader>
          <Input value={typedConfirmation} onChange={(e) => setTypedConfirmation(e.target.value)} placeholder="ENABLE" autoFocus data-testid="discovery-confirm-input" />
          <DialogFooter className="gap-2 sm:gap-2">
            <Button variant="outline" onClick={() => setConfirmStep(0)}>Cancel</Button>
            <Button onClick={persist} disabled={typedConfirmation.trim().toUpperCase() !== 'ENABLE' || saving}>
              {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Turn on sharing
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <UpgradeDialog details={gate.details} onClose={gate.close} />
    </>
  );
}
