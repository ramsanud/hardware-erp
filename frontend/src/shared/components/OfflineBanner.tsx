import { useEffect, useRef, useState } from 'react';
import { Wifi, WifiOff } from 'lucide-react';
import { useOnlineStatus } from '@/shared/hooks/useOnlineStatus';
import { cn } from '@/shared/lib/utils';

/** How long "Back online" stays before the bar folds away. */
const RECOVERED_MS = 2500;

/**
 * CR-100. A full-width strip under the app bar while the browser is offline.
 *
 * The app has no offline mode (CR-043 was never built, and the owner has
 * ruled one out), so a request made now will fail. Saying so before the
 * user presses Save - rather than after, through a generic "cannot reach the
 * server" - is the entire job of this component. It says nothing while
 * everything is fine, and "Back online" for a moment after recovery so the
 * bar does not simply vanish mid-glance.
 */
export function OfflineBanner({ className }: { className?: string }) {
  const online = useOnlineStatus();
  const [recovered, setRecovered] = useState(false);
  const wasOffline = useRef(false);

  useEffect(() => {
    if (!online) {
      wasOffline.current = true;
      setRecovered(false);
      return undefined;
    }
    if (!wasOffline.current) return undefined;
    wasOffline.current = false;
    setRecovered(true);
    const timer = window.setTimeout(() => setRecovered(false), RECOVERED_MS);
    return () => window.clearTimeout(timer);
  }, [online]);

  if (online && !recovered) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      data-offline-banner={online ? 'recovered' : 'offline'}
      className={cn(
        'flex items-center justify-center gap-2 px-4 py-2 text-center text-sm font-medium',
        online ? 'bg-success text-success-foreground' : 'bg-warning text-warning-foreground',
        className,
      )}
    >
      {online ? <Wifi className="h-4 w-4 shrink-0" aria-hidden /> : <WifiOff className="h-4 w-4 shrink-0" aria-hidden />}
      <span>
        {online
          ? 'Back online.'
          : 'You are offline. Changes cannot be saved until the connection returns.'}
      </span>
    </div>
  );
}
