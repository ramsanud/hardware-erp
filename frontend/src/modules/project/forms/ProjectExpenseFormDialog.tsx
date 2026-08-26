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
import { PROJECT_EXPENSE_CATEGORY_OPTIONS } from '../constants';
import { projectExpenseSchema, type ProjectExpenseValues } from '../validation/schemas';
import type { ProjectExpenseRequest } from '../types';

interface ProjectExpenseFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (request: ProjectExpenseRequest) => Promise<void>;
}

/**
 * LABOUR/EMPLOYEE categories are manual today - see ProjectExpense's
 * backend class comment. This form works for every category equally; the
 * only thing that changes once a Labour/Team module exists later is where
 * those two categories' rows come from.
 */
export function ProjectExpenseFormDialog({ open, onOpenChange, onSubmit }: ProjectExpenseFormDialogProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    control, handleSubmit, register, reset, formState: { errors, isSubmitting },
  } = useForm<ProjectExpenseValues>({
    resolver: zodResolver(projectExpenseSchema),
    defaultValues: { category: 'LABOUR', expenseDate: new Date().toISOString().slice(0, 10), paidTo: '', description: '' },
  });

  const close = () => {
    reset({ category: 'LABOUR', expenseDate: new Date().toISOString().slice(0, 10), paidTo: '', description: '' });
    setFormError(null);
    onOpenChange(false);
  };

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit({
        category: values.category,
        amountPaise: Math.round(values.amountRupees * 100),
        expenseDate: values.expenseDate,
        paidTo: values.paidTo || null,
        description: values.description || null,
      });
      close();
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.');
    }
  });

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!isSubmitting) { if (!next) close(); else onOpenChange(next); } }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>Add project expense</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}

          <FormField id="expenseCategory" label="Category" required>
            <Controller control={control} name="category" render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="expenseCategory"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {PROJECT_EXPENSE_CATEGORY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )} />
          </FormField>

          <FormField id="expenseAmount" label="Amount (₹)" error={errors.amountRupees?.message} required>
            <Controller control={control} name="amountRupees" render={({ field }) => (
              <NumberInput id="expenseAmount" min={0} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )} />
          </FormField>

          <FormField id="expenseDate" label="Date" error={errors.expenseDate?.message} required>
            <Input id="expenseDate" type="date" {...register('expenseDate')} />
          </FormField>

          <FormField id="paidTo" label="Paid to (optional)" hint="e.g. a labour team or shop name">
            <Input id="paidTo" {...register('paidTo')} />
          </FormField>

          <FormField id="expenseDescription" label="Description (optional)">
            <Input id="expenseDescription" {...register('description')} />
          </FormField>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={close} disabled={isSubmitting}>Cancel</Button>
            <Button type="submit" loading={isSubmitting}>Add expense</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
