import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { INDIAN_STATES } from '@/shared/data/indianStates';
import { useToast } from '@/modules/auth/hooks/useToast';
import { supplierService } from '../services/supplierService';
import type { SupplierSummaryResponse } from '../types';

/**
 * Only the two fields the API actually requires, plus the two a hardware shop
 * will reach for immediately. The full supplier record is a multi-step wizard
 * (CR-017); dragging that into a dialog mid-purchase would be the opposite of
 * a quick add. Everything else is editable later on the supplier page.
 *
 * Mirrors the Bean Validation on SupplierRequest exactly, so a value this
 * form accepts is never rejected by the server.
 */
const quickSupplierSchema = z.object({
  supplierName: z.string().trim().min(1, 'Supplier name is required').max(255),
  mobileNo: z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number'),
  gstNo: z.string().trim().toUpperCase()
    .regex(/^$|^\d{2}[A-Z]{5}\d{4}[A-Z][0-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN')
    .optional().or(z.literal('')),
  stateCode: z.string().trim().optional().or(z.literal('')),
});
type QuickSupplierValues = z.infer<typeof quickSupplierSchema>;

interface SupplierQuickAddDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Prefills the name from whatever the owner had already typed into the picker. */
  initialName?: string;
  onCreated: (supplier: SupplierSummaryResponse) => void;
}

/**
 * "The supplier isn't in the list yet" - add it without losing the purchase
 * being entered (BUG-FE-022). Same contract as WorkTypeQuickAddDialog and the
 * product picker's inline add: create, auto-select, stay on the form.
 */
export function SupplierQuickAddDialog({
  open, onOpenChange, initialName, onCreated,
}: SupplierQuickAddDialogProps) {
  const toast = useToast();
  const [saving, setSaving] = useState(false);
  const {
    register, handleSubmit, reset, setValue, watch, formState: { errors },
  } = useForm<QuickSupplierValues>({
    resolver: zodResolver(quickSupplierSchema),
    defaultValues: { supplierName: '', mobileNo: '', gstNo: '', stateCode: '' },
  });

  // Carry across what was already typed into the search box, so the owner does
  // not retype the name they just searched for and found nothing for.
  useEffect(() => {
    if (open) reset({ supplierName: initialName ?? '', mobileNo: '', gstNo: '', stateCode: '' });
  }, [open, initialName, reset]);

  const stateCode = watch('stateCode');

  const submit = handleSubmit(async (values) => {
    setSaving(true);
    try {
      const created = await supplierService.create({
        supplierName: values.supplierName,
        mobileNo: values.mobileNo,
        // null, not "" - @Pattern passes null but rejects an empty string.
        gstNo: values.gstNo || null,
        stateCode: values.stateCode || null,
        // supplierCode omitted entirely so the server generates SUP-nnnn.
        // These two are required by the API and have no sensible prompt in a
        // quick add: cash on delivery, no credit ceiling. Both are editable on
        // the supplier page, which is where a credit decision belongs anyway.
        paymentTermsDays: 0,
        creditLimitPaise: 0,
        // A supplier added mid-purchase is one you are buying from right now.
        status: 'ACTIVE',
      });
      toast.success(`"${created.supplierName}" added.`);
      // Built explicitly rather than cast: SupplierResponse carries more than
      // the summary, and a cast would silently pass whatever shape the API
      // returns straight into the picker.
      onCreated({
        id: created.id,
        supplierCode: created.supplierCode,
        supplierName: created.supplierName,
        contactPerson: created.contactPerson,
        mobileNo: created.mobileNo,
        city: created.city,
        gstNo: created.gstNo,
        paymentTermsDays: created.paymentTermsDays,
        creditLimitDisplay: created.creditLimitDisplay,
        status: created.status,
      });
      onOpenChange(false);
    } catch (caught) {
      toast.error(caught, 'Could not add this supplier.');
    } finally {
      setSaving(false);
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Add supplier</DialogTitle>
          <DialogDescription>
            Just enough to record the purchase. You can complete the rest on the supplier page later.
          </DialogDescription>
        </DialogHeader>

        <form id="supplier-quick-add" onSubmit={submit} className="space-y-4" noValidate>
          <FormField id="qa-supplierName" label="Supplier name" error={errors.supplierName?.message} required>
            <Input id="qa-supplierName" autoFocus {...register('supplierName')} />
          </FormField>

          <FormField id="qa-mobileNo" label="Mobile number" error={errors.mobileNo?.message} required>
            <Input id="qa-mobileNo" inputMode="numeric" maxLength={10} {...register('mobileNo')} />
          </FormField>

          <FormField id="qa-gstNo" label="GSTIN (optional)" error={errors.gstNo?.message}>
            <Input id="qa-gstNo" className="uppercase" maxLength={15} {...register('gstNo')} />
          </FormField>

          <FormField id="qa-stateCode" label="State (optional)" error={errors.stateCode?.message}>
            <Select value={stateCode || ''}
                    onValueChange={(value) => setValue('stateCode', value, { shouldValidate: true })}>
              <SelectTrigger id="qa-stateCode"><SelectValue placeholder="Select a state" /></SelectTrigger>
              <SelectContent>
                {INDIAN_STATES.map((state) => (
                  <SelectItem key={state.code} value={state.code}>
                    {state.name} ({state.code})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
        </form>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="supplier-quick-add" loading={saving}>Add supplier</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
