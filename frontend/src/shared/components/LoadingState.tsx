import { Loader2 } from 'lucide-react';
import { cn } from '@/shared/lib/utils';

interface LoadingStateProps {
  /** Read by screen readers and shown under the spinner when `showLabel` is set. */
  label?: string;
  /** `page` fills the viewport (route guards, lazy routes); `section` sits inside a card or list. */
  variant?: 'page' | 'section';
  showLabel?: boolean;
  className?: string;
}

/**
 * CR-100. The one spinner for "nothing is here yet".
 *
 * ProtectedRoute, the lazy landing route and every "still fetching" section
 * drew their own Loader2 with slightly different sizes and no accessible
 * name. One component keeps them identical and announces itself.
 */
export function LoadingState({
  label = 'Loading', variant = 'section', showLabel = false, className,
}: LoadingStateProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'flex flex-col items-center justify-center gap-3 text-muted-foreground',
        variant === 'page' ? 'h-dvh' : 'px-6 py-16',
        className,
      )}
    >
      <Loader2 className="h-6 w-6 animate-spin" aria-hidden />
      <span className={showLabel ? 'text-sm' : 'sr-only'}>{label}</span>
    </div>
  );
}
