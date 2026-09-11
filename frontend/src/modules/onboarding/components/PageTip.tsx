import { Lightbulb, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import type { usePageTips } from '../hooks/usePageTips';

/**
 * CR-075. The first-visit tip banner, rendered above the page content.
 *
 * A banner and not a dialog: a tip must never block the screen it is
 * describing. It sits in the flow, pushes the page down by its own height,
 * and is gone for good with one tap. Theme tokens only - the accent is the
 * primary colour at low alpha, so it reads as "the app is talking" in every
 * theme rather than as a hard-coded yellow (see memory: never hardcoded
 * colours).
 */
export function PageTip({ tips }: { tips: ReturnType<typeof usePageTips> }) {
  const { tip, dismiss, turnOff } = tips;
  if (!tip) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      data-page-tip={tip.id}
      className="mb-4 flex items-start gap-3 rounded-lg border border-primary/30 bg-primary/5 px-3 py-2.5 sm:px-4"
    >
      <span
        className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary"
        aria-hidden
      >
        <Lightbulb className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium leading-snug">{tip.title}</p>
        <p className="mt-0.5 text-sm leading-relaxed text-muted-foreground">{tip.body}</p>
        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
          <Button type="button" size="sm" variant="outline" onClick={dismiss}>
            Got it
          </Button>
          <button
            type="button"
            onClick={turnOff}
            className="text-xs text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
          >
            Turn off tips
          </button>
        </div>
      </div>
      {/* The X is "Got it" for people who never read buttons. */}
      <Button
        type="button" variant="ghost" size="icon"
        className="-mr-1 -mt-1 h-8 w-8 shrink-0"
        onClick={dismiss}
        aria-label="Dismiss tip"
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  );
}
