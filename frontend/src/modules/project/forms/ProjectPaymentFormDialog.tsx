import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { PAYMENT_METHOD_OPTIONS } from '@/modules/invoice/constants';
import { projectPaymentSchema, type ProjectPaymentValues } from '../validation/schemas';
import type { ProjectPaymentRequest } from '../types';

interface ProjectPaymentFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (request: ProjectPaymentRequest) => Promise<void>;
}

export function ProjectPaymentFormDialog({ open, onOpenChange, onSubmit }: ProjectPaymentFormDialogProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    control, handleSubmit, register, reset, formState: { errors, isSubmitting },
  } = useForm<ProjectPaymentValues>({
    resolver: zodResolver(projectPaymentSchema),
    defaultValues: { paymentMethod: 'CASH', paymentDate: new Date().toISOString().slice(0, 10), notes: '' },
  });

  const close = () => {
    reset({ paymentMethod: 'CASH', paymentDate: new Date().toISOString().slice(0, 10), notes: '' });
    setFormError(null);
    onOpenChange(false);
  };

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit({
        amountPaise: Math.round(values.amountRupees * 100),
        paymentMethod: values.paymentMethod,
        paymentDate: values.paymentDate,
        notes: values.notes || null,
      });
      close();
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.');
    }
  });

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!isSubmitting) { if (!next) close(); else onOpenChange(next); } }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>Record a payment</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}

          <FormField id="paymentAmount" label="Amount (₹)" error={errors.amountRupees?.message} required>
            <Controller control={control} name="amountRupees" render={({ field }) => (
              <NumberInput id="paymentAmount" min={0} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )} />
          </FormField>

          <FormField id="paymentMethod" label="Payment method" required>
            <Controller control={control} name="paymentMethod" render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="paymentMethod"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {PAYMENT_METHOD_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )} />
          </FormField>

          <FormField id="paymentDate" label="Date" error={errors.paymentDate?.message} required>
            <Input id="paymentDate" type="date" {...register('paymentDate')} />
          </FormField>

          <FormField id="paymentNotes" label="Notes (optional)">
            <Input id="paymentNotes" {...register('notes')} />
          </FormField>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={close} disabled={isSubmitting}>Cancel</Button>
            <Button type="submit" loading={isSubmitting}>Record payment</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
