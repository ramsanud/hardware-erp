import { useCallback, useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Loader2, LocateFixed, MapPin, Search } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { stateNameForCode } from '@/shared/data/indianStates';
import {
  reverseGeocode, searchPlaces, type GeoPoint, type PickedAddress,
} from '@/shared/lib/geocoding';
import { cn } from '@/shared/lib/utils';

const TILE_URL = import.meta.env.VITE_MAP_TILE_URL?.trim() || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const TILE_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors';

/** Where the map opens with nothing to go on: the whole country, not a blank ocean. */
const INDIA_CENTRE: L.LatLngExpression = [22.5, 79];
const INDIA_ZOOM = 5;
const STREET_ZOOM = 17;

/**
 * Leaflet's stock marker is a PNG whose path Vite rewrites out from under
 * it (the classic broken-icon problem). An inline SVG painted from the
 * theme's own --primary sidesteps that and matches every colour theme.
 * The className replaces Leaflet's default `leaflet-div-icon`, which would
 * otherwise draw a white box behind the pin.
 */
const PIN_ICON = L.divIcon({
  className: 'address-map-pin',
  iconSize: [28, 36],
  iconAnchor: [14, 36],
  html: '<svg viewBox="0 0 24 24" width="28" height="36" fill="hsl(var(--primary))" stroke="hsl(var(--primary-foreground))" stroke-width="1" aria-hidden="true">'
    + '<path d="M12 22s7-7.1 7-12.5A7 7 0 0 0 5 9.5C5 14.9 12 22 12 22z"/><circle cx="12" cy="9.5" r="2.6" fill="hsl(var(--primary-foreground))" stroke="none"/></svg>',
});

export interface AddressMapDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** See AddressMapPicker: centres the map near what the form already holds; nothing is pre-selected. */
  current?: { addressLine1?: string | null; city?: string | null; pincode?: string | null };
  onPick: (address: PickedAddress) => void;
}

type Status = 'idle' | 'locating' | 'error';

/**
 * The map half of AddressMapPicker, in its own module so Leaflet (~40 kB
 * gzipped) is fetched the first time someone opens the map rather than on
 * every page load. Default export because React.lazy wants one.
 */
export default function AddressMapDialog({ open, onOpenChange, current, onPick }: AddressMapDialogProps) {
  const [picked, setPicked] = useState<PickedAddress | null>(null);
  const [status, setStatus] = useState<Status>('idle');
  const [message, setMessage] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState<PickedAddress[]>([]);

  // State, not a ref: Radix renders its Portal empty on the first pass and
  // fills it from a layout effect, so a ref is still null when an effect
  // keyed on `open` runs. A callback ref re-runs the effect once the div exists.
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const inFlight = useRef<AbortController | null>(null);

  const abortInFlight = () => {
    inFlight.current?.abort();
    inFlight.current = null;
  };

  const resolvePoint = useCallback(async (point: GeoPoint) => {
    abortInFlight();
    const controller = new AbortController();
    inFlight.current = controller;
    setStatus('locating');
    setMessage(null);
    setResults([]);
    try {
      const address = await reverseGeocode(point, controller.signal);
      if (controller.signal.aborted) return;
      if (!address) {
        setPicked(null);
        setStatus('error');
        setMessage('No address is known at that spot. Try a little closer to a road.');
        return;
      }
      setPicked(address);
      setStatus('idle');
    } catch (error) {
      if (controller.signal.aborted) return;
      setPicked(null);
      setStatus('error');
      setMessage(error instanceof Error && error.name !== 'TypeError'
        ? error.message
        : 'Could not reach the map service. Check the connection, or type the address instead.');
    }
  }, []);

  const placeMarker = useCallback((point: GeoPoint, zoom?: number) => {
    const map = mapRef.current;
    if (!map) return;
    if (!markerRef.current) {
      markerRef.current = L.marker(point, { draggable: true, icon: PIN_ICON, keyboard: false }).addTo(map);
      markerRef.current.on('dragend', () => {
        const at = markerRef.current?.getLatLng();
        if (at) void resolvePoint({ lat: at.lat, lng: at.lng });
      });
    } else {
      markerRef.current.setLatLng(point);
    }
    if (zoom) map.setView(point, Math.max(map.getZoom(), zoom));
    else map.panTo(point);
  }, [resolvePoint]);

  // Build the map once its container is in the DOM, tear it down when the
  // dialog closes and the container unmounts. Leaflet measures its container at creation; the sheet is
  // still animating in at that moment, so the size is re-read once the
  // animation has finished and again whenever the container changes shape
  // (the phone rotating, the keyboard closing).
  useEffect(() => {
    if (!container) return;
    const map = L.map(container, { center: INDIA_CENTRE, zoom: INDIA_ZOOM, zoomControl: true });
    L.tileLayer(TILE_URL, { attribution: TILE_ATTRIBUTION, maxZoom: 19 }).addTo(map);
    map.on('click', (event: L.LeafletMouseEvent) => {
      const point = { lat: event.latlng.lat, lng: event.latlng.lng };
      placeMarker(point);
      void resolvePoint(point);
    });
    mapRef.current = map;

    const settle = window.setTimeout(() => map.invalidateSize(), 250);
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(container);

    // Editing an existing record: start near it rather than over the whole
    // country. A failed lookup just leaves the default view.
    const hint = [current?.addressLine1, current?.city, current?.pincode].filter(Boolean).join(', ');
    const controller = new AbortController();
    if (hint) {
      searchPlaces(hint, controller.signal)
        .then((found) => { if (found[0] && !controller.signal.aborted && !markerRef.current) map.setView(found[0].point, 15); })
        .catch(() => undefined);
    }

    return () => {
      controller.abort();
      abortInFlight();
      window.clearTimeout(settle);
      observer.disconnect();
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
    // `current` is read once, at open; a keystroke in the form must not rebuild the map.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [container, placeMarker, resolvePoint]);

  const handleOpenChange = (next: boolean) => {
    onOpenChange(next);
    if (!next) {
      setPicked(null);
      setStatus('idle');
      setMessage(null);
      setQuery('');
      setResults([]);
    }
  };

  const runSearch = async () => {
    const q = query.trim();
    if (!q || searching) return;
    abortInFlight();
    const controller = new AbortController();
    inFlight.current = controller;
    setSearching(true);
    setMessage(null);
    try {
      const found = await searchPlaces(q, controller.signal);
      if (controller.signal.aborted) return;
      setResults(found);
      if (found.length === 0) setMessage('Nothing found for that search. Try a landmark, area or pincode.');
    } catch {
      if (!controller.signal.aborted) setMessage('Could not reach the map service. Check the connection, or type the address instead.');
    } finally {
      if (!controller.signal.aborted) setSearching(false);
    }
  };

  const chooseResult = (address: PickedAddress) => {
    setResults([]);
    setQuery('');
    placeMarker(address.point, STREET_ZOOM);
    // A search hit already carries its address; resolving again would only
    // spend one of Nominatim's one-per-second requests to learn the same thing.
    setPicked(address);
    setStatus('idle');
    setMessage(null);
  };

  const useMyLocation = () => {
    if (!('geolocation' in navigator)) {
      setMessage('This browser cannot share its location.');
      return;
    }
    setStatus('locating');
    setMessage(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const point = { lat: position.coords.latitude, lng: position.coords.longitude };
        placeMarker(point, STREET_ZOOM);
        void resolvePoint(point);
      },
      (error) => {
        setStatus('error');
        setMessage(error.code === error.PERMISSION_DENIED
          ? 'Location access was refused. Allow it in the browser, or tap the map instead.'
          : 'Could not read the current location. Tap the map instead.');
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  };

  const apply = () => {
    if (!picked) return;
    onPick(picked);
    handleOpenChange(false);
  };

  const stateName = picked ? stateNameForCode(picked.stateCode) : undefined;

  return (
    <>
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Choose the address on the map</DialogTitle>
            <DialogDescription>
              Tap the spot, drag the pin to adjust, or search for a landmark. The address fields are filled from
              the pin and can still be edited afterwards.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative flex-1">
              <Input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    void runSearch();
                  }
                }}
                placeholder="Search a landmark, area or pincode"
                aria-label="Search the map"
                autoComplete="off"
                enterKeyHint="search"
              />
            </div>
            <div className="flex gap-2">
              <Button type="button" variant="secondary" onClick={() => void runSearch()} loading={searching}
                      disabled={!query.trim()} className="flex-1 sm:flex-none">
                <Search className="h-4 w-4" aria-hidden />
                Search
              </Button>
              <Button type="button" variant="outline" onClick={useMyLocation} className="flex-1 sm:flex-none">
                <LocateFixed className="h-4 w-4" aria-hidden />
                My location
              </Button>
            </div>
          </div>

          {results.length > 0 ? (
            <ul className="max-h-40 divide-y overflow-y-auto rounded-md border text-sm" aria-label="Search results">
              {results.map((result) => (
                <li key={`${result.point.lat},${result.point.lng}`}>
                  <button type="button" onClick={() => chooseResult(result)}
                          className="flex w-full items-start gap-2 px-3 py-2 text-left hover:bg-accent focus-visible:bg-accent focus-visible:outline-none">
                    <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                    <span className="line-clamp-2">{result.displayName}</span>
                  </button>
                </li>
              ))}
            </ul>
          ) : null}

          {/* `isolate` keeps Leaflet's high z-index panes inside the map instead
              of floating over the dialog's close button and footer. */}
          <div ref={setContainer} role="application" aria-label="Map"
               className="relative isolate z-0 h-64 w-full overflow-hidden rounded-md border bg-muted sm:h-80" />

          <div className="min-h-[3.5rem] rounded-md border bg-muted/40 px-3 py-2 text-sm" aria-live="polite">
            {status === 'locating' ? (
              <span className="inline-flex items-center gap-2 text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Finding the address…
              </span>
            ) : picked ? (
              <div className="space-y-0.5">
                <p className="font-medium">
                  {[picked.addressLine1, picked.addressLine2].filter(Boolean).join(', ') || picked.displayName}
                </p>
                <p className="text-muted-foreground">
                  {[picked.city, stateName, picked.pincode].filter(Boolean).join(', ') || 'City, state and pincode not known here - fill them in by hand.'}
                </p>
              </div>
            ) : (
              <span className={cn(message ? 'text-destructive' : 'text-muted-foreground')}>
                {message ?? 'No spot chosen yet.'}
              </span>
            )}
            {picked && message ? <p className="mt-1 text-destructive">{message}</p> : null}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>Cancel</Button>
            <Button type="button" onClick={apply} disabled={!picked || status === 'locating'}>
              Use this address
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
