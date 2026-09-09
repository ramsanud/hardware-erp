import { useNavigate } from 'react-router-dom';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { cn } from '@/shared/lib/utils';
import type { useOnboardingTour } from '../hooks/useOnboardingTour';

/**
 * CR-075. The first-visit walkthrough.
 *
 * A dialog rather than spotlight bubbles pinned to elements around the page.
 * That is a deliberate trade: coach marks anchored to a rail entry break the
 * moment the rail is collapsed, the entry is hidden behind a permission, or
 * the viewport is a phone where the rail is a drawer that is not on screen at
 * all - all three of which are normal states here (CR-061). A dialog says the
 * same thing at every width and cannot point at something that is not there.
 */
export function WelcomeTour({ tour }: { tour: ReturnType<typeof useOnboardingTour> }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { open, steps, step, index, isFirst, isLast, next, back, close } = tour;

  // A user whose permissions match no step at all would otherwise get an empty
  // shell. Nothing to say is a reason to say nothing.
  if (!step || steps.length === 0) return null;

  const StepIcon = step.icon;
  const firstName = user?.fullName?.trim().split(/\s+/)[0];

  const goToAction = () => {
    if (!step.action) return;
    close();
    navigate(step.action.to);
  };

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => { if (!nextOpen) close(); }}>
      {/*
        Closing by any route - the X, Escape, the overlay - lands on close(),
        which records the tour as seen. Anything else would re-offer it on the
        next page load to someone who has just dismissed it twice.
      */}
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          {isFirst && firstName ? (
            <p className="text-sm text-muted-foreground">
              Welcome, {firstName}. Here is what you can do here.
            </p>
          ) : null}
          <div className="flex items-start gap-3 pt-1">
            <span
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
              aria-hidden
            >
              <StepIcon className="h-5 w-5" />
            </span>
            <div className="min-w-0 flex-1 text-left">
              <DialogTitle className="text-left">{step.title}</DialogTitle>
              <DialogDescription className="pt-1.5 text-left leading-relaxed">
                {step.body}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {step.action ? (
          <Button type="button" variant="outline" className="w-full" onClick={goToAction}>
            {step.action.label}
          </Button>
        ) : null}

        {/*
          Dots are decoration - the same progress is stated in words below for
          anyone who cannot see them, which is why they are aria-hidden rather
          than a list of eleven unlabelled items.
        */}
        <div className="flex items-center justify-center gap-1.5 pt-1" aria-hidden>
          {steps.map((dot, dotIndex) => (
            <span
              key={dot.id}
              className={cn(
                'h-1.5 rounded-full transition-all',
                dotIndex === index ? 'w-4 bg-primary' : 'w-1.5 bg-muted-foreground/30',
              )}
            />
          ))}
        </div>

        <DialogFooter className="sm:justify-between">
          {/*
            Skip is always present and never hidden behind a menu. A tour you
            cannot leave is an obstacle, and the shop floor is not the place to
            trap someone in an eleven-step slideshow.
          */}
          <Button type="button" variant="ghost" onClick={close}>
            {isLast ? 'Close' : 'Skip tour'}
          </Button>
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground" aria-live="polite">
              Step {index + 1} of {steps.length}
            </span>
            {!isFirst ? (
              <Button type="button" variant="outline" onClick={back}>Back</Button>
            ) : null}
            <Button type="button" onClick={isLast ? close : next}>
              {isLast ? 'Finish' : 'Next'}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
