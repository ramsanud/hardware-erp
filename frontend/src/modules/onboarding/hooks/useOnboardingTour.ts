import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { readScoped, writeScoped } from '@/theme/themeScope';
import { TOUR_STEPS, TOUR_STORAGE_KEY, TOUR_VERSION } from '../constants/tourSteps';
import type { TourStep } from '../constants/tourSteps';

/**
 * CR-075. Whether the tour is showing, what it is showing, and remembering
 * that it has been seen.
 *
 * Stored client-side, scoped by user id through the same `themeScope` helper
 * the theme and CR-068's column choices already use. That is the precedent
 * CR-068 set for presentation state with no business consequence, and this is
 * the same kind of state: whether one person has read an introduction is not a
 * fact the shop's books need to survive a device change. The consequence is
 * honest and worth stating - sign in on a new phone and the tour offers itself
 * again, which for a once-per-user welcome is closer to right than wrong.
 * "It must follow me between devices" is a new CR and a real table, exactly as
 * CR-068 says.
 */
export function useOnboardingTour() {
  const { user, hasPermission } = useAuth();
  const [open, setOpen] = useState(false);
  const [index, setIndex] = useState(0);

  /**
   * Only steps this person could actually act on. An accountant is not shown
   * the stock walkthrough, because the rail will not offer them /stock either.
   */
  const steps = useMemo<TourStep[]>(
    () => TOUR_STEPS.filter(
      (step) => !step.permissions || step.permissions.some((permission) => hasPermission(permission)),
    ),
    [hasPermission],
  );

  const markSeen = useCallback(() => {
    writeScoped(TOUR_STORAGE_KEY, TOUR_VERSION);
  }, []);

  /**
   * Offer the tour once, to a signed-in user who has not seen this version.
   *
   * Keyed on user?.id rather than running once on mount: the scope only
   * becomes the real user id after AuthProvider resolves, so reading any
   * earlier would test the "guest" key and re-offer the tour to everyone on
   * every sign-in.
   */
  useEffect(() => {
    if (!user) return;
    if (readScoped(TOUR_STORAGE_KEY) === TOUR_VERSION) return;
    setIndex(0);
    setOpen(true);
  }, [user?.id]);

  /** Skip and Finish are the same promise - do not ask me again - so both record it. */
  const close = useCallback(() => {
    markSeen();
    setOpen(false);
  }, [markSeen]);

  /** Reopening deliberately does NOT clear the flag; finishing it again re-records it. */
  const restart = useCallback(() => {
    setIndex(0);
    setOpen(true);
  }, []);

  const next = useCallback(() => {
    setIndex((current) => {
      if (current >= steps.length - 1) return current;
      return current + 1;
    });
  }, [steps.length]);

  const back = useCallback(() => setIndex((current) => Math.max(0, current - 1)), []);

  // A shrinking step list (a permission revoked mid-session, or a shorter tour
  // after a version bump) must not strand the index past the end.
  const safeIndex = Math.min(index, Math.max(0, steps.length - 1));

  return {
    open,
    setOpen,
    steps,
    step: steps[safeIndex],
    index: safeIndex,
    isFirst: safeIndex === 0,
    isLast: safeIndex >= steps.length - 1,
    next,
    back,
    close,
    restart,
  };
}
