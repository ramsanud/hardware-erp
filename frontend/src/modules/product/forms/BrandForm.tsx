import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { BRAND_STATUS_OPTIONS } from '../constants';
import { brandSchema, type BrandValues } from '../validation/schemas';
import type { BrandResponse } from '../types';

interface BrandFormProps {
  /** Undefined means create. */
  brand?: BrandResponse;
  onSubmit: (values: BrandValues) => Promise<void>;
  onCancel: () => void;
}

export function BrandForm({ brand, onSubmit, onCancel }: BrandFormProps) {
  const isEdit = Boolean(brand);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<BrandValues>({
    resolver: zodResolver(brandSchema),
    defaultValues: {
      brandCode: brand?.brandCode ?? '',
      brandName: brand?.brandName ?? '',
      description: brand?.description ?? '',
      status: brand?.status ?? 'ACTIVE',
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof BrandValues, { message });
        });
        if (error.code === 'DUPLICATE_RESOURCE') {
          const lower = error.message.toLowerCase();
          if (lower.includes('code')) setError('brandCode', { message: error.message });
          else if (lower.includes('name')) setError('brandName', { message: error.message });
        }
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

      <FormField id="brandName" label="Brand name" error={errors.brandName?.message} required>
        <Input id="brandName" autoFocus aria-invalid={Boolean(errors.brandName)}
               {...register('brandName')} />
      </FormField>

      <FormField id="brandCode" label="Brand code" error={errors.brandCode?.message}
                 hint={isEdit ? undefined : 'Generated automatically. Only set this to match a code you already use.'}>
        <Input id="brandCode" placeholder={isEdit ? undefined : "Auto (BRD-0004)"} className="uppercase"
               aria-invalid={Boolean(errors.brandCode)} {...register('brandCode')} />
      </FormField>

      <FormField id="description" label="Description" error={errors.description?.message}>
        <Input id="description" aria-invalid={Boolean(errors.description)}
               {...register('description')} />
      </FormField>

      <FormField id="status" label="Status" error={errors.status?.message} required>
        <Controller
          control={control}
          name="status"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="status"><SelectValue /></SelectTrigger>
              <SelectContent>
                {BRAND_STATUS_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </FormField>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create brand'}
        </Button>
      </DialogFooter>
    </form>
  );
}
