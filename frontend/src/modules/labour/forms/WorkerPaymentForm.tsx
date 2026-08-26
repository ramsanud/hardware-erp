import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/components/ui/select';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { PAYMENT_METHOD_OPTIONS } from '@/modules/invoice/constants';
import { workerPaymentSchema, type WorkerPaymentValues } from '../validation/schemas';

export const WORKER_PAYMENT_FORM_ID = 'worker-payment-form';

interface WorkerPaymentFormProps {
  onSubmit: (values: WorkerPaymentValues) => Promise<void>;
  onCancel: () => void;
}

export function WorkerPaymentForm({ onSubmit, onCancel }: WorkerPaymentFormProps) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<WorkerPaymentValues>({
    resolver: zodResolver(workerPaymentSchema),
    defaultValues: {
      amountRupees: '',
      paymentDate: new Date().toISOString().slice(0, 10),
      paymentMethod: 'CASH',
      notes: '',
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof WorkerPaymentValues, { message });
        });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form id={WORKER_PAYMENT_FORM_ID} onSubmit={submit} className="space-y-4" noValidate>
      {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="amountRupees" label="Amount (₹)" error={errors.amountRupees?.message} required>
          <Input id="amountRupees" type="number" min={0} step="0.01"
                 aria-invalid={Boolean(errors.amountRupees)} {...register('amountRupees')} />
        </FormField>

        <FormField id="paymentDate" label="Date" error={errors.paymentDate?.message} required>
          <Input id="paymentDate" type="date" aria-invalid={Boolean(errors.paymentDate)}
                 {...register('paymentDate')} />
        </FormField>

        <FormField id="paymentMethod" label="Payment method" error={errors.paymentMethod?.message} required>
          <Controller
            control={control}
            name="paymentMethod"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="paymentMethod"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {PAYMENT_METHOD_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>

        <FormField id="notes" label="Notes" error={errors.notes?.message}>
          <Input id="notes" aria-invalid={Boolean(errors.notes)} {...register('notes')} />
        </FormField>
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Record payment</Button>
      </DialogFooter>
    </form>
  );
}
