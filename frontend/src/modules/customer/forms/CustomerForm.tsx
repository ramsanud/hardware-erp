import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { INDIAN_STATES } from '@/shared/data/indianStates';
import { customerSchema, type CustomerValues } from '../validation/schemas';
import type { CustomerRequest, CustomerResponse } from '../types';

export const CUSTOMER_FORM_ID = 'customer-form';

interface CustomerFormProps {
  /** Undefined means create. */
  customer?: CustomerResponse;
  onSubmit: (request: CustomerRequest) => Promise<void>;
  onCancel: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

export function CustomerForm({ customer, onSubmit, onCancel, onDirtyChange }: CustomerFormProps) {
  const isEdit = Boolean(customer);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, setError, setValue, watch, formState: { errors, isSubmitting, isDirty },
  } = useForm<CustomerValues>({
    resolver: zodResolver(customerSchema),
    defaultValues: {
      customerName: customer?.customerName ?? '',
      mobileNo: customer?.mobileNo ?? '',
      email: customer?.email ?? '',
      gstNo: customer?.gstNo ?? '',
      addressLine1: customer?.addressLine1 ?? '',
      addressLine2: customer?.addressLine2 ?? '',
      city: customer?.city ?? '',
      stateCode: customer?.stateCode ?? '',
      pincode: customer?.pincode ?? '',
      creditLimitRupees: customer ? Number(customer.creditLimitDisplay.replace(/,/g, '')) : 0,
      status: customer?.status ?? 'ACTIVE',
    },
  });
  const values = watch();

  useEffect(() => { onDirtyChange(isDirty); }, [isDirty, onDirtyChange]);

  const submit = handleSubmit(async (form) => {
    setFormError(null);
    try {
      await onSubmit({
        customerName: form.customerName,
        mobileNo: form.mobileNo,
        email: form.email || null,
        gstNo: form.gstNo || null,
        addressLine1: form.addressLine1 || null,
        addressLine2: form.addressLine2 || null,
        city: form.city || null,
        stateCode: form.stateCode || null,
        pincode: form.pincode || null,
        creditLimitPaise: form.creditLimitRupees ? Math.round(form.creditLimitRupees * 100) : 0,
        status: form.status,
      });
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message);
        if (error.code === 'DUPLICATE_RESOURCE') setError('mobileNo', { message: error.message });
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form id={CUSTOMER_FORM_ID} onSubmit={submit} noValidate className="space-y-4">
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="customerName" label="Customer name" error={errors.customerName?.message} required
                   className="sm:col-span-2">
          <Input id="customerName" autoFocus aria-invalid={Boolean(errors.customerName)} {...register('customerName')} />
        </FormField>
        <FormField id="mobileNo" label="Mobile number" error={errors.mobileNo?.message} required
                   hint={isEdit ? undefined : "A returning customer's existing record is reused automatically."}>
          <Input id="mobileNo" inputMode="numeric" maxLength={10} placeholder="9876543210"
                 aria-invalid={Boolean(errors.mobileNo)} {...register('mobileNo')} />
        </FormField>
        <FormField id="email" label="Email" error={errors.email?.message}>
          <Input id="email" type="email" aria-invalid={Boolean(errors.email)} {...register('email')} />
        </FormField>
        <FormField id="gstNo" label="GSTIN" error={errors.gstNo?.message}>
          <Input id="gstNo" maxLength={15} className="uppercase" aria-invalid={Boolean(errors.gstNo)} {...register('gstNo')} />
        </FormField>
        {isEdit ? (
          <FormField id="status" label="Status" error={errors.status?.message}
                     hint="An inactive customer stays visible in history - invoices/quotations already made for them are untouched. Set back to Active to let staff pick them for a new sale again.">
            <Controller
              control={control}
              name="status"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="status"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVE">Active</SelectItem>
                    <SelectItem value="INACTIVE">Inactive</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        ) : null}
        <FormField id="creditLimitRupees" label="Credit limit (₹)" error={errors.creditLimitRupees?.message}>
          <Controller
            control={control}
            name="creditLimitRupees"
            render={({ field }) => (
              <NumberInput id="creditLimitRupees" min={0} aria-invalid={Boolean(errors.creditLimitRupees)}
                           value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>
        <FormField id="addressLine1" label="Address line 1" error={errors.addressLine1?.message} className="sm:col-span-2">
          <Input id="addressLine1" {...register('addressLine1')} />
        </FormField>
        <FormField id="addressLine2" label="Address line 2" error={errors.addressLine2?.message} className="sm:col-span-2">
          <Input id="addressLine2" {...register('addressLine2')} />
        </FormField>
        <FormField id="city" label="City" error={errors.city?.message}>
          <Input id="city" {...register('city')} />
        </FormField>
        <FormField id="pincode" label="Pincode" error={errors.pincode?.message}>
          <Input id="pincode" inputMode="numeric" maxLength={6} {...register('pincode')} />
        </FormField>
        <FormField id="stateName" label="State" hint="Fills in the GST state code automatically.">
          <Select
            value={INDIAN_STATES.find((s) => s.code === values.stateCode)?.name ?? ''}
            onValueChange={(name) => {
              const state = INDIAN_STATES.find((s) => s.name === name);
              if (state) setValue('stateCode', state.code, { shouldDirty: true, shouldValidate: true });
            }}
          >
            <SelectTrigger id="stateName"><SelectValue placeholder="Select a state" /></SelectTrigger>
            <SelectContent>
              {INDIAN_STATES.map((state) => (
                <SelectItem key={state.code} value={state.name}>{state.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>
        <FormField id="stateCode" label="GST state code" error={errors.stateCode?.message}>
          <Input id="stateCode" inputMode="numeric" maxLength={2} aria-invalid={Boolean(errors.stateCode)}
                 {...register('stateCode')} />
        </FormField>
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>{isEdit ? 'Save changes' : 'Add customer'}</Button>
      </DialogFooter>
    </form>
  );
}
