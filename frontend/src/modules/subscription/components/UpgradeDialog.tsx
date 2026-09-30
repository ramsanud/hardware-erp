import { Lock } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { SUBSCRIPTION_ROUTES } from '../constants';
import type { FeatureNotAvailableDetails } from '../lib/featureNotAvailable';

interface UpgradeDialogProps {
  details: FeatureNotAvailableDetails | null;
  onClose: () => void;
}

/**
 * CR-088 §12. The professional "you need a higher plan" dialog every
 * 403 FEATURE_NOT_AVAILABLE should surface - never a raw toast for this
 * specific error, and never blocking anything but the one feature that
 * was actually refused.
 */
export function UpgradeDialog({ details, onClose }: UpgradeDialogProps) {
  return (
    <Dialog open={details !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-primary/10">
            <Lock className="h-5 w-5 text-primary" />
          </div>
          <DialogTitle>Feature not available</DialogTitle>
          <DialogDescription>
            {details ? (
              <>
                &ldquo;{details.featureName}&rdquo; is available in the{' '}
                <span className="font-medium text-foreground">{details.requiredPlanName ?? 'a higher'}</span> plan.
                <br />
                Current plan: <span className="font-medium text-foreground">{details.currentPlanName}</span>
                {details.requiredPlanName ? (
                  <>
                    {' '}· Required plan: <span className="font-medium text-foreground">{details.requiredPlanName}</span>
                  </>
                ) : null}
              </>
            ) : null}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-2">
          <Button variant="outline" onClick={onClose}>Not now</Button>
          <Button asChild onClick={onClose}>
            <Link to={SUBSCRIPTION_ROUTES.pricing}>View plans</Link>
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
