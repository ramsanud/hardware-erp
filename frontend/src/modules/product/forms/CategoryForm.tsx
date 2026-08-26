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
import { CATEGORY_STATUS_OPTIONS } from '../constants';
import { categorySchema, type CategoryValues } from '../validation/schemas';
import type { CategoryResponse } from '../types';

interface CategoryFormProps {
  /** Undefined means create. */
  category?: CategoryResponse;
  /** Every other category, for the parent picker. Self and descendants are excluded by the caller. */
  categories: CategoryResponse[];
  onSubmit: (values: CategoryValues) => Promise<void>;
  onCancel: () => void;
}

const NONE = '__none__';

export function CategoryForm({ category, categories, onSubmit, onCancel }: CategoryFormProps) {
  const isEdit = Boolean(category);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<CategoryValues>({
    resolver: zodResolver(categorySchema),
    defaultValues: {
      categoryCode: category?.categoryCode ?? '',
      categoryName: category?.categoryName ?? '',
      parentCategoryId: category?.parentCategoryId ?? null,
      description: category?.description ?? '',
      status: category?.status ?? 'ACTIVE',
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof CategoryValues, { message });
        });
        if (error.code === 'DUPLICATE_RESOURCE') {
          const lower = error.message.toLowerCase();
          if (lower.includes('code')) setError('categoryCode', { message: error.message });
          else if (lower.includes('name')) setError('categoryName', { message: error.message });
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

      <FormField id="categoryName" label="Category name" error={errors.categoryName?.message} required>
        <Input id="categoryName" autoFocus aria-invalid={Boolean(errors.categoryName)}
               {...register('categoryName')} />
      </FormField>

      <FormField id="categoryCode" label="Category code" error={errors.categoryCode?.message}
                 hint={isEdit ? undefined : 'Generated automatically. Only set this to match a code you already use.'}>
        <Input id="categoryCode" placeholder={isEdit ? undefined : "Auto (CAT-0003)"} className="uppercase"
               aria-invalid={Boolean(errors.categoryCode)} {...register('categoryCode')} />
      </FormField>

      <FormField id="parentCategoryId" label="Parent category" error={errors.parentCategoryId?.message}
                 hint="Leave blank for a top-level category.">
        <Controller
          control={control}
          name="parentCategoryId"
          render={({ field }) => (
            <Select
              value={field.value ? String(field.value) : NONE}
              onValueChange={(value) => field.onChange(value === NONE ? null : Number(value))}
            >
              <SelectTrigger id="parentCategoryId"><SelectValue placeholder="None" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>None (top level)</SelectItem>
                {categories.map((option) => (
                  <SelectItem key={option.id} value={String(option.id)}>{option.categoryName}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
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
                {CATEGORY_STATUS_OPTIONS.map((option) => (
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
          {isEdit ? 'Save changes' : 'Create category'}
        </Button>
      </DialogFooter>
    </form>
  );
}
