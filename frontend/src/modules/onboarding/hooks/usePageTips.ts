import { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { readScoped, writeScoped } from '@/theme/themeScope';
import {
  PAGE_TIPS_OFF_KEY, PAGE_TIPS_SEEN_KEY, tipForPath,
} from '../constants/pageTips';
import type { PageTip } from '../constants/pageTips';

function readSeen(): Set<string> {
  try {
    const raw = readScoped(PAGE_TIPS_SEEN_KEY);
    return new Set(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set();
  }
}

/**
 * CR-075. The tip for the current page, if this user has not dismissed it.
 *
 * Two dismissals, and the difference is the whole design: "Got it" hides
 * THIS tip and the next screen still gets its own, while "Turn off tips"
 * hides them all. Someone who has closed four tips in a row is telling you
 * something, and making them close twenty more is how a help feature
 * becomes the thing people remember disliking.
 */
export function usePageTips() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const [tip, setTip] = useState<PageTip | null>(null);

  useEffect(() => {
    // Nothing until the scope is the real user - otherwise the first tip is
    // read from and written to the "guest" scope and re-shown after sign-in.
    if (!user) { setTip(null); return; }
    if (readScoped(PAGE_TIPS_OFF_KEY) === '1') { setTip(null); return; }
    const candidate = tipForPath(pathname);
    if (!candidate || readSeen().has(candidate.id)) { setTip(null); return; }
    setTip(candidate);
  }, [pathname, user?.id]);

  const dismiss = useCallback(() => {
    if (!tip) return;
    const seen = readSeen();
    seen.add(tip.id);
    writeScoped(PAGE_TIPS_SEEN_KEY, JSON.stringify([...seen]));
    setTip(null);
  }, [tip]);

  const turnOff = useCallback(() => {
    writeScoped(PAGE_TIPS_OFF_KEY, '1');
    setTip(null);
  }, []);

  return { tip, dismiss, turnOff };
}

/**
 * Called when the tour is replayed on purpose: asking "how does this work?"
 * again is the clearest possible signal that the tips are wanted back.
 */
export function turnPageTipsBackOn(): void {
  writeScoped(PAGE_TIPS_OFF_KEY, '0');
  writeScoped(PAGE_TIPS_SEEN_KEY, '[]');
}
