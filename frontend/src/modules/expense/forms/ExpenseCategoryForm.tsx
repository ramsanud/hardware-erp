import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { expenseCategorySchema, type ExpenseCategoryValues } from '../validation/schemas';

interface ExpenseCategoryFormProps {
  onSubmit: (values: ExpenseCategoryValues) => Promise<void>;
  onCancel: () => void;
}

export function ExpenseCategoryForm({ onSubmit, onCancel }: ExpenseCategoryFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<ExpenseCategoryValues>({
    resolver: zodResolver(expenseCategorySchema),
    defaultValues: { name: '', description: '' },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === 'DUPLICATE_RESOURCE') setError('name', { message: error.message });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}
      <FormField id="categoryName" label="Category name" error={errors.name?.message} required
                 hint='e.g. "Rent", "Salaries", "Utilities"'>
        <Input id="categoryName" autoFocus aria-invalid={Boolean(errors.name)} {...register('name')} />
      </FormField>
      <FormField id="categoryDescription" label="Description" error={errors.description?.message}>
        <Input id="categoryDescription" {...register('description')} />
      </FormField>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Add category</Button>
      </DialogFooter>
    </form>
  );
}
