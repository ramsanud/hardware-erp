import type { ReactNode } from 'react';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';

interface LegalDocumentDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  subtitle: string;
  version: string;
  lastUpdated: string;
  /** Fired by "I understand" - marks the document reviewed and closes. */
  onAcknowledge: () => void;
  children: ReactNode;
}

/**
 * A full legal document, sized to actually be read.
 *
 * Wider than the default dialog (which tops out at max-w-lg) because legal
 * copy set to ~60 characters in a narrow column is what makes people scroll
 * past it. On a phone it fills the screen instead, since a centred card with
 * margins would leave even less room.
 *
 * DialogContent already handles the mechanics this needs - focus trap, Escape,
 * focus restore to the trigger, a scrolling body with the header and footer
 * pinned - so the page behind never scrolls while this is open.
 */
export function LegalDocumentDialog({
  open, onOpenChange, title, subtitle, version, lastUpdated, onAcknowledge, children,
}: LegalDocumentDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="h-[100dvh] max-h-[100dvh] w-full max-w-none rounded-none
                   sm:h-auto sm:max-h-[min(80vh,760px)] sm:w-[min(900px,92vw)] sm:max-w-none sm:rounded-lg"
      >
        <DialogHeader>
          <DialogTitle className="text-xl">{title}</DialogTitle>
          <DialogDescription>{subtitle}</DialogDescription>
          <p className="pt-1 text-xs text-muted-foreground">
            <span className="font-medium">Version {version}</span>
            <span aria-hidden> · </span>
            Last updated {lastUpdated}
          </p>
        </DialogHeader>

        {/* leading-relaxed here rather than on every paragraph: line height is
            what makes a wall of legal text readable. */}
        <div className="space-y-6 py-2 leading-relaxed">
          {children}
        </div>

        <DialogFooter className="sm:justify-between">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button type="button" onClick={onAcknowledge}>
            I understand
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
