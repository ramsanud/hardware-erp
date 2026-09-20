import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/shared/components/ui/alert';
import { Button } from '@/shared/components/ui/button';

interface PartialDataNoticeProps {
  /** Human names of the sections that did not load, e.g. "Sales growth". */
  failed: string[];
  onRetry?: () => void;
  className?: string;
}

/**
 * CR-100. For a page assembled from several independent requests.
 *
 * The dashboard fires ten calls on mount and each one fails on its own; a
 * card whose call failed used to show "—" exactly as it does for a shop with
 * no data yet, so a half-broken backend and a brand-new shop looked the
 * same. This says which parts are missing and offers one retry for all of
 * them. It renders nothing when nothing failed, so the page's happy path is
 * untouched.
 */
export function PartialDataNotice({ failed, onRetry, className }: PartialDataNoticeProps) {
  if (failed.length === 0) return null;

  return (
    <Alert variant="warning" data-partial-data={failed.length} className={className}>
      <AlertTriangle aria-hidden />
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <AlertTitle>Some of this page did not load</AlertTitle>
          <AlertDescription className="text-foreground/80">
            {failed.length === 1
              ? `${failed[0]} could not be loaded. Everything else is up to date.`
              : `${failed.length} sections could not be loaded: ${failed.join(', ')}. Everything else is up to date.`}
          </AlertDescription>
        </div>
        {onRetry ? (
          <Button variant="outline" size="sm" onClick={onRetry} className="w-fit shrink-0 text-foreground">
            <RefreshCw className="h-4 w-4" />
            Retry
          </Button>
        ) : null}
      </div>
    </Alert>
  );
}
