import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { readScoped, scopedStorageKey, writeScoped } from '@/theme/themeScope';
import { TOUR_STEPS, TOUR_STORAGE_KEY, TOUR_VERSION } from '../constants/tourSteps';
import type { TourStep } from '../constants/tourSteps';
import { turnPageTipsBackOn } from './usePageTips';

/**
 * Where a paused tour was, so "Open dashboard" followed by a reload still
 * comes back to step 2 rather than to nothing. sessionStorage on purpose:
 * a pause is a thing you did in THIS tab in the last few minutes, not a
 * preference to carry to next week.
 */
const PAUSED_KEY = 'hardware-erp-tour-paused';

function readPaused(): number | null {
  try {
    const raw = sessionStorage.getItem(scopedStorageKey(PAUSED_KEY));
    if (raw === null) return null;
    const index = Number(raw);
    return Number.isInteger(index) && index >= 0 ? index : null;
  } catch {
    return null;
  }
}

function writePaused(index: number | null): void {
  try {
    const key = scopedStorageKey(PAUSED_KEY);
    if (index === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, String(index));
  } catch {
    // Storage unavailable - the in-memory state still carries this tab.
  }
}

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
 *
 * Three states, not two. `open` is the dialog on screen; `paused` is the tour
 * stepped aside so you can look at the page it just described, with a way
 * back; neither is "finished". Before `paused` existed, "Open dashboard"
 * closed the tour for good - the one button that said "go and look" was also
 * the one that made sure you could never come back to where you were.
 */
export function useOnboardingTour() {
  const { user, hasPermission } = useAuth();
  const [open, setOpen] = useState(false);
  const [paused, setPaused] = useState(false);
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
    writePaused(null);
  }, []);

  /**
   * Offer the tour once, to a signed-in user who has not seen this version -
   * or, if they paused it in this tab, put the resume control back instead of
   * starting over.
   *
   * Keyed on user?.id rather than running once on mount: the scope only
   * becomes the real user id after AuthProvider resolves, so reading any
   * earlier would test the "guest" key and re-offer the tour to everyone on
   * every sign-in.
   */
  useEffect(() => {
    if (!user) return;
    const pausedAt = readPaused();
    if (pausedAt !== null) {
      setIndex(pausedAt);
      setPaused(true);
      setOpen(false);
      return;
    }
    if (readScoped(TOUR_STORAGE_KEY) === TOUR_VERSION) return;
    setIndex(0);
    setOpen(true);
  }, [user?.id]);

  /** Skip and Finish are the same promise - do not ask me again - so both record it. */
  const close = useCallback(() => {
    markSeen();
    setPaused(false);
    setOpen(false);
  }, [markSeen]);

  /**
   * Step aside without finishing. The dialog goes, the page is usable, and a
   * resume control carries the step you were on until you come back or end
   * it. Nothing is written to the seen flag: pausing is not dismissing.
   */
  const pause = useCallback(() => {
    setOpen(false);
    setPaused(true);
    writePaused(index);
  }, [index]);

  const resume = useCallback(() => {
    writePaused(null);
    setPaused(false);
    setOpen(true);
  }, []);

  /**
   * Reopening deliberately does NOT clear the seen flag - finishing again
   * re-records it. It DOES bring the per-page tips back: asking "how does
   * this work?" is the one unambiguous signal that they are wanted.
   */
  const restart = useCallback(() => {
    turnPageTipsBackOn();
    writePaused(null);
    setPaused(false);
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
    paused,
    setOpen,
    steps,
    step: steps[safeIndex],
    index: safeIndex,
    isFirst: safeIndex === 0,
    isLast: safeIndex >= steps.length - 1,
    next,
    back,
    close,
    pause,
    resume,
    restart,
  };
}
