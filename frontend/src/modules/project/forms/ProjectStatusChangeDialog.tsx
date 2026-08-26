import { useState } from 'react';
import { Button } from '@/shared/components/ui/button';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { PROJECT_STATUS_OPTIONS } from '../constants';
import type { ProjectOutcome, ProjectStatus, ProjectStatusChangeRequest } from '../types';

interface ProjectStatusChangeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentStatus: ProjectStatus;
  onSubmit: (request: ProjectStatusChangeRequest) => Promise<void>;
}

/** COMPLETED requires an outcome; every other status forbids one - mirrors the backend's own rule exactly (ProjectServiceImpl.changeStatus), so the dialog never lets a user submit a combination the API would reject. */
export function ProjectStatusChangeDialog({ open, onOpenChange, currentStatus, onSubmit }: ProjectStatusChangeDialogProps) {
  const [status, setStatus] = useState<ProjectStatus>(currentStatus);
  const [outcome, setOutcome] = useState<ProjectOutcome | ''>('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (status === 'COMPLETED' && !outcome) {
      setError('Mark this project as a success or a failure to complete it.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSubmit({ status, outcome: status === 'COMPLETED' ? (outcome as ProjectOutcome) : null });
      onOpenChange(false);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not update the status.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!saving) onOpenChange(next); }}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Change project status</DialogTitle></DialogHeader>
        <div className="space-y-4">
          {error ? <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert> : null}

          <FormField id="newStatus" label="Status" required>
            <Select value={status} onValueChange={(v) => { setStatus(v as ProjectStatus); setOutcome(''); }}>
              <SelectTrigger id="newStatus"><SelectValue /></SelectTrigger>
              <SelectContent>
                {PROJECT_STATUS_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>

          {status === 'COMPLETED' ? (
            <FormField id="outcome" label="Outcome" required hint="A completed project must be marked a success or a failure.">
              <Select value={outcome} onValueChange={(v) => setOutcome(v as ProjectOutcome)}>
                <SelectTrigger id="outcome"><SelectValue placeholder="Select an outcome" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="SUCCESS">Success</SelectItem>
                  <SelectItem value="FAILURE">Failure</SelectItem>
                </SelectContent>
              </Select>
            </FormField>
          ) : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>Cancel</Button>
          <Button type="button" onClick={submit} loading={saving}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
