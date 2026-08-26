import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { workerSchema, type WorkerValues } from '../validation/schemas';
import type { WorkerResponse } from '../types';

export const WORKER_FORM_ID = 'worker-form';

interface WorkerFormProps {
  /** Undefined means create. */
  worker?: WorkerResponse;
  onSubmit: (values: WorkerValues) => Promise<void>;
  onCancel: () => void;
}

export function WorkerForm({ worker, onSubmit, onCancel }: WorkerFormProps) {
  const isEdit = Boolean(worker);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<WorkerValues>({
    resolver: zodResolver(workerSchema),
    defaultValues: {
      name: worker?.name ?? '',
      mobileNo: worker?.mobileNo ?? '',
      roleTitle: worker?.roleTitle ?? '',
      dailyRateRupees: worker ? String(worker.dailyRatePaise / 100) : '',
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof WorkerValues, { message });
        });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form id={WORKER_FORM_ID} onSubmit={submit} className="space-y-4" noValidate>
      {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="name" label="Name" error={errors.name?.message} required className="sm:col-span-2">
          <Input id="name" aria-invalid={Boolean(errors.name)} {...register('name')} />
        </FormField>

        <FormField id="mobileNo" label="Mobile number" error={errors.mobileNo?.message}>
          <Input id="mobileNo" aria-invalid={Boolean(errors.mobileNo)} {...register('mobileNo')} />
        </FormField>

        <FormField id="roleTitle" label="Role / skill" error={errors.roleTitle?.message}>
          <Input id="roleTitle" placeholder="e.g. Mason, Electrician"
                 aria-invalid={Boolean(errors.roleTitle)} {...register('roleTitle')} />
        </FormField>

        <FormField id="dailyRateRupees" label="Daily rate (₹)" error={errors.dailyRateRupees?.message} required>
          <Input id="dailyRateRupees" type="number" min={0} step="0.01"
                 aria-invalid={Boolean(errors.dailyRateRupees)} {...register('dailyRateRupees')} />
        </FormField>
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>{isEdit ? 'Save changes' : 'Add worker'}</Button>
      </DialogFooter>
    </form>
  );
}
