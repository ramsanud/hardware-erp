import { lazy, Suspense, useState } from 'react';
import { MapPin } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import type { PickedAddress } from '@/shared/lib/geocoding';

// Leaflet only loads when the map is first opened - see AddressMapDialog.
const AddressMapDialog = lazy(() => import('@/shared/components/AddressMapDialog'));

interface AddressMapPickerProps {
  /**
   * What the form already holds. Used only to centre the map near the
   * existing address when editing; nothing is pre-selected, so an
   * accidental tap cannot overwrite a precise typed address with a
   * city-centre guess.
   */
  current?: { addressLine1?: string | null; city?: string | null; pincode?: string | null };
  onPick: (address: PickedAddress) => void;
  disabled?: boolean;
  className?: string;
}

/**
 * CR-076. "Pick on map" beside the address fields. Opens a map (a bottom
 * sheet on phones, like every other dialog since CR-061); tap, drag the pin,
 * search a place, or use the phone's GPS, and the resolved address is
 * previewed before it is written into the form. The typed fields stay
 * editable afterwards - the map is a faster way to fill them, not a
 * replacement for them, and a wrong match is corrected the same way as a
 * typo.
 *
 * Nothing is persisted beyond the address text: no coordinates column
 * exists, so this changes no API contract and no table.
 */
export function AddressMapPicker({ current, onPick, disabled, className }: AddressMapPickerProps) {
  const [open, setOpen] = useState(false);
  // Once opened, the dialog stays mounted (closed) so its exit animation
  // plays and Leaflet is not re-fetched on the second open.
  const [mounted, setMounted] = useState(false);

  return (
    <>
      <Button type="button" variant="outline" size="sm" disabled={disabled} className={className}
              onClick={() => { setMounted(true); setOpen(true); }}>
        <MapPin className="h-4 w-4" aria-hidden />
        Pick on map
      </Button>
      {mounted ? (
        <Suspense fallback={null}>
          <AddressMapDialog open={open} onOpenChange={setOpen} current={current} onPick={onPick} />
        </Suspense>
      ) : null}
    </>
  );
}
