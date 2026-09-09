import { useState } from 'react';
import { Button } from '@/shared/components/ui/button';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel?: string;
  destructive?: boolean;
  onConfirm: () => Promise<void> | void;
}

/**
 * Destructive actions never fire straight from a menu click. The dialog stays
 * open while the request is in flight so the user is not left wondering
 * whether it worked.
 */
export function ConfirmDialog({
  open, onOpenChange, title, description,
  confirmLabel = 'Confirm', destructive = false, onConfirm,
}: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false);

  const handleConfirm = async () => {
    setBusy(true);
    try {
      await onConfirm();
      onOpenChange(false);
    } catch {
      /*
       * Swallowed on purpose, and only here. Callers such as
       * ProductListPage.handleDelete deliberately re-throw after toasting, so
       * that this dialog stays open on a failure instead of closing over an
       * action that did not happen - which is exactly what the missing
       * onOpenChange(false) above achieves. Without this catch the re-thrown
       * error escaped an async onClick handler with nothing awaiting it and
       * surfaced as an unhandled promise rejection on every failed delete.
       * The user-facing message is the caller's job; the dialog's job is just
       * to remain open and re-enable its button.
       */
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !busy && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant={destructive ? 'destructive' : 'default'}
            onClick={handleConfirm}
            loading={busy}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
