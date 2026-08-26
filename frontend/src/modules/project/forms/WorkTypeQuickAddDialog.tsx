import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { useToast } from '@/modules/auth/hooks/useToast';
import { workTypeService } from '../services/workTypeService';
import { workTypeSchema, type WorkTypeValues } from '../validation/schemas';
import type { WorkTypeResponse } from '../types';

interface WorkTypeQuickAddDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (workType: WorkTypeResponse) => void;
}

/**
 * "If a new work type doesn't exist, add it" (request §4) - a project's
 * own work-type select opens this instead of sending the user away to a
 * separate settings page. The new type is auto-selected on success.
 */
export function WorkTypeQuickAddDialog({ open, onOpenChange, onCreated }: WorkTypeQuickAddDialogProps) {
  const toast = useToast();
  const [saving, setSaving] = useState(false);
  const {
    register, handleSubmit, reset, formState: { errors },
  } = useForm<WorkTypeValues>({ resolver: zodResolver(workTypeSchema), defaultValues: { name: '', description: '' } });

  const submit = handleSubmit(async (values) => {
    setSaving(true);
    try {
      const created = await workTypeService.create({ name: values.name, description: values.description || null });
      toast.success(`"${created.name}" added.`);
      reset();
      onCreated(created);
      onOpenChange(false);
    } catch (caught) {
      toast.error(caught, 'Could not add this work type.');
    } finally {
      setSaving(false);
    }
  });

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!saving) onOpenChange(next); }}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Add work type</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormField id="workTypeName" label="Name" error={errors.name?.message} required>
            <Input id="workTypeName" autoFocus placeholder="e.g. Aluminium Partition" {...register('name')} />
          </FormField>
          <FormField id="workTypeDescription" label="Description (optional)" error={errors.description?.message}>
            <Input id="workTypeDescription" {...register('description')} />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>Cancel</Button>
            <Button type="submit" loading={saving}>Add</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
