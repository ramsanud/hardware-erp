import { useEffect, useState } from 'react';
import { CalendarDays } from 'lucide-react';

/**
 * CR-082. Today's date and the shop clock, top-right of the dashboard.
 *
 * The clock ticks once a minute, not once a second: a seconds counter on a
 * dashboard draws the eye every second for no reason, and the figure it sits
 * beside changes once a day. Rendered in the browser's own zone - a shop's
 * counter and its browser are in the same room.
 */
export function ShopTimeChip() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const tick = () => setNow(new Date());
    // Align the first tick to the next minute boundary so the display never
    // sits up to 59s stale, then every 60s after.
    const untilNextMinute = 60_000 - (Date.now() % 60_000);
    let interval: ReturnType<typeof setInterval> | undefined;
    const timeout = setTimeout(() => {
      tick();
      interval = setInterval(tick, 60_000);
    }, untilNextMinute);
    return () => {
      clearTimeout(timeout);
      if (interval) clearInterval(interval);
    };
  }, []);

  const date = now.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
  const time = now.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });

  return (
    <div className="flex items-center gap-2.5 text-sm">
      <CalendarDays className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
      <div className="leading-tight">
        <p className="font-medium">{date}</p>
        <p className="text-xs text-muted-foreground">
          Shop Time: <time dateTime={now.toISOString()}>{time}</time>
        </p>
      </div>
    </div>
  );
}

/** "Good morning" / "Good afternoon" / "Good evening", from the browser's clock. */
export function greetingFor(hour: number): string {
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}
