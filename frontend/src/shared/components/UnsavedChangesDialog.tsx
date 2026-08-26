import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { Button } from '@/shared/components/ui/button';

interface UnsavedChangesDialogProps {
  open: boolean;
  /** "Continue Editing" - just closes this prompt, the form stays open untouched. */
  onContinueEditing: () => void;
  onDiscard: () => void;
  /** id of the still-mounted <form> underneath (this dialog layers on top, it never unmounts the form) - "Save" submits it directly. */
  formId: string;
  saving?: boolean;
}

/**
 * Shared by every dialog/wizard form with unsaved-changes protection
 * (CR-023) - Product, Customer, Supplier. A form only shows this when
 * react-hook-form's isDirty is true; a clean form closes immediately with
 * no prompt.
 */
export function UnsavedChangesDialog({
  open, onContinueEditing, onDiscard, formId, saving = false,
}: UnsavedChangesDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) onContinueEditing(); }}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Unsaved changes</DialogTitle>
          <DialogDescription>
            You have unsaved changes. Do you want to discard them?
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onContinueEditing} disabled={saving}>
            Continue Editing
          </Button>
          <Button type="button" variant="outline" onClick={onDiscard} disabled={saving}
                  className="text-destructive hover:text-destructive">
            Discard Changes
          </Button>
          <Button type="submit" form={formId} loading={saving}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
