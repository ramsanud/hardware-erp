import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Label } from '@/shared/components/ui/label';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { supplierContactSchema, type SupplierContactValues } from '../validation/schemas';
import type { SupplierContactResponse } from '../types';

interface ContactFormProps {
  /** Undefined means add. */
  contact?: SupplierContactResponse;
  onSubmit: (values: SupplierContactValues) => Promise<void>;
  onCancel: () => void;
}

export function ContactForm({ contact, onSubmit, onCancel }: ContactFormProps) {
  const isEdit = Boolean(contact);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<SupplierContactValues>({
    resolver: zodResolver(supplierContactSchema),
    defaultValues: {
      contactName: contact?.contactName ?? '',
      designation: contact?.designation ?? '',
      mobileNo: contact?.mobileNo ?? '',
      email: contact?.email ?? '',
      primary: contact?.primary ?? false,
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof SupplierContactValues, { message });
        });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      <FormField id="contactName" label="Contact name" error={errors.contactName?.message} required>
        <Input id="contactName" autoFocus aria-invalid={Boolean(errors.contactName)}
               {...register('contactName')} />
      </FormField>

      <FormField id="designation" label="Designation" error={errors.designation?.message}>
        <Input id="designation" placeholder="Sales Manager"
               aria-invalid={Boolean(errors.designation)} {...register('designation')} />
      </FormField>

      <FormField id="mobileNo" label="Mobile number" error={errors.mobileNo?.message} required>
        <Input id="mobileNo" inputMode="numeric" maxLength={10} placeholder="9876543210"
               aria-invalid={Boolean(errors.mobileNo)} {...register('mobileNo')} />
      </FormField>

      <FormField id="email" label="Email" error={errors.email?.message}>
        <Input id="email" type="email" aria-invalid={Boolean(errors.email)} {...register('email')} />
      </FormField>

      <Controller
        control={control}
        name="primary"
        render={({ field }) => (
          <label htmlFor="primary" className="flex cursor-pointer items-start gap-2.5">
            <Checkbox
              id="primary"
              checked={field.value}
              onCheckedChange={(checked) => field.onChange(checked === true)}
              className="mt-0.5"
            />
            <span>
              <Label htmlFor="primary" className="cursor-pointer">Primary contact</Label>
              <span className="mt-0.5 block text-sm text-muted-foreground">
                Setting this clears the flag on any other contact for this supplier.
              </span>
            </span>
          </label>
        )}
      />

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Add contact'}
        </Button>
      </DialogFooter>
    </form>
  );
}
