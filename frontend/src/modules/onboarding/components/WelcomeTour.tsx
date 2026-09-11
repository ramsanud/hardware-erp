import { useNavigate } from 'react-router-dom';
import { Play, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useAppChrome } from '@/layouts/AppChromeProvider';
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
  const { brandName } = useAppChrome();
  const { open, paused, steps, step, index, isFirst, isLast, next, back, close, pause, resume } = tour;

  // A user whose permissions match no step at all would otherwise get an empty
  // shell. Nothing to say is a reason to say nothing.
  if (!step || steps.length === 0) return null;

  const StepIcon = step.icon;
  const firstName = user?.fullName?.trim().split(/\s+/)[0];
  // The areas this person can actually reach - the permission filter has
  // already run, so listing the surviving steps IS the role description.
  const areas = steps.map((item) => item.area).filter((area): area is string => Boolean(area));
  const isWelcome = step.id === 'welcome';

  /**
   * "Open dashboard" is an invitation to go and look, not to leave. The tour
   * steps aside (pause, not close - nothing is marked seen) and a resume
   * control follows you to the destination, so the one button that says
   * "go and see" is no longer also the one that ends the tour.
   */
  const goToAction = () => {
    if (!step.action) return;
    pause();
    navigate(step.action.to);
  };

  if (paused && !open) {
    return (
      <div
        role="status"
        data-tour-paused
        className="fixed inset-x-0 bottom-20 z-40 flex justify-center px-4 lg:bottom-6"
      >
        {/*
          bottom-20 clears the phone tab bar; lg:bottom-6 sits in the corner
          gutter on desktop. Centred rather than bottom-right, where the AI
          chat widget already lives, so the two never stack.
        */}
        <div className="pointer-events-auto flex items-center gap-1 rounded-full border bg-background/95 py-1 pl-1 pr-1 shadow-lg backdrop-blur supports-[backdrop-filter]:bg-background/80">
          <Button type="button" size="sm" className="rounded-full" onClick={resume}>
            <Play className="h-3.5 w-3.5" />
            Continue tour
            <span className="ml-1 text-xs font-normal opacity-80">{index + 1} of {steps.length}</span>
          </Button>
          <Button
            type="button" variant="ghost" size="icon" className="h-8 w-8 rounded-full"
            onClick={close}
            aria-label="End tour"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      </div>
    );
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => { if (!nextOpen) close(); }}>
      {/*
        Closing by any route - the X, Escape, the overlay - lands on close(),
        which records the tour as seen. Anything else would re-offer it on the
        next page load to someone who has just dismissed it twice.
      */}
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <div className="flex items-start gap-3 pt-1">
            <span
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
              aria-hidden
            >
              <StepIcon className="h-5 w-5" />
            </span>
            <div className="min-w-0 flex-1 text-left">
              <DialogTitle className="text-left">
                {isWelcome ? `Welcome${firstName ? `, ${firstName}` : ''}` : step.title}
              </DialogTitle>
              {isWelcome ? (
                <DialogDescription className="pt-1.5 text-left leading-relaxed" data-tour-welcome>
                  {/* Either half can be missing - a shop with no name set yet,
                      or a user whose role label has not loaded - and the
                      sentence has to survive both without a dangling "to as". */}
                  You are signed in
                  {brandName ? <> to <span className="font-medium text-foreground">{brandName}</span></> : null}
                  {user?.roleName ? <> as <span className="font-medium text-foreground">{user.roleName}</span></> : null}.
                  {areas.length > 0
                    ? ` This short tour shows the ${areas.length} areas you can work with. Skip it any time - the ? button brings it back.`
                    : ' This short tour shows you around. Skip it any time - the ? button brings it back.'}
                </DialogDescription>
              ) : (
                <DialogDescription className="pt-1.5 text-left leading-relaxed">
                  {step.body}
                </DialogDescription>
              )}
            </div>
          </div>
        </DialogHeader>

        {isWelcome && areas.length > 0 ? (
          <ul className="flex flex-wrap gap-1.5" aria-label="Areas you can work with">
            {areas.map((area) => (
              <li
                key={area}
                className="rounded-full border border-primary/30 bg-primary/5 px-2.5 py-0.5 text-xs font-medium text-foreground"
              >
                {area}
              </li>
            ))}
          </ul>
        ) : null}

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
            {/* Focus starts on the way FORWARD. Radix would otherwise put it
                on Skip as the first focusable, so Enter - the key people press
                to mean "continue" - would have dismissed the whole tour. */}
            <Button type="button" autoFocus onClick={isLast ? close : next}>
              {isLast ? 'Finish' : 'Next'}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
