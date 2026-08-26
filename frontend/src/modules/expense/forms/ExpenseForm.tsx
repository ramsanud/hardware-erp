import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ImageIcon, Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ImageUpload } from '@/shared/components/ImageUpload';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { ApiError } from '@/shared/types/api';
import { PAYMENT_METHOD_OPTIONS } from '@/modules/invoice/constants';
import { expenseCategoryService } from '../services/expenseCategoryService';
import { expenseService } from '../services/expenseService';
import { expenseSchema, type ExpenseValues } from '../validation/schemas';
import { ExpenseCategoryForm } from './ExpenseCategoryForm';
import type { BusinessExpenseResponse, ExpenseCategoryResponse } from '../types';
import type { ExpenseCategoryValues } from '../validation/schemas';

export const EXPENSE_FORM_ID = 'expense-form';

const ADD_NEW = '__add_new__';

interface ExpenseFormProps {
  /** Undefined means create. */
  expense?: BusinessExpenseResponse;
  categories: ExpenseCategoryResponse[];
  onSubmit: (values: ExpenseValues) => Promise<void>;
  onCancel: () => void;
  onCategoryCreated?: (category: ExpenseCategoryResponse) => void;
  onReceiptChanged?: () => void;
}

export function ExpenseForm({
  expense, categories, onSubmit, onCancel, onCategoryCreated, onReceiptChanged,
}: ExpenseFormProps) {
  const isEdit = Boolean(expense);
  const [formError, setFormError] = useState<string | null>(null);
  const [addingCategory, setAddingCategory] = useState(false);

  const [receiptVersion, setReceiptVersion] = useState(0);
  const [hasReceipt, setHasReceipt] = useState(expense?.hasReceipt ?? false);
  const receiptSrc = useAuthenticatedImage(
    isEdit && hasReceipt && expense ? expenseService.receiptUrl(expense.id) : null, receiptVersion);

  const {
    register, control, handleSubmit, setError, setValue, formState: { errors, isSubmitting },
  } = useForm<ExpenseValues>({
    resolver: zodResolver(expenseSchema),
    defaultValues: {
      expenseDate: expense?.expenseDate ?? new Date().toISOString().slice(0, 10),
      categoryId: expense?.categoryId ?? (undefined as unknown as number),
      amountRupees: expense ? String(expense.amountPaise / 100) : '',
      paymentMethod: expense?.paymentMethod ?? 'CASH',
      notes: expense?.notes ?? '',
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof ExpenseValues, { message });
        });
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  const handleCreateCategory = async (values: ExpenseCategoryValues) => {
    const created = await expenseCategoryService.create({
      name: values.name,
      description: values.description || null,
    });
    onCategoryCreated?.(created);
    setValue('categoryId', created.id, { shouldDirty: true, shouldValidate: true });
    setAddingCategory(false);
  };

  return (
    <>
      <form id={EXPENSE_FORM_ID} onSubmit={submit} className="space-y-4" noValidate>
        {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}

        {isEdit && expense ? (
          <ImageUpload
            src={receiptSrc}
            alt={`Receipt for ${expense.categoryName}`}
            shape="square"
            fallback={<ImageIcon className="h-8 w-8 text-muted-foreground" aria-hidden />}
            onUpload={async (file) => {
              await expenseService.uploadReceipt(expense.id, file);
              setReceiptVersion((v) => v + 1);
              setHasReceipt(true);
              onReceiptChanged?.();
            }}
            onRemove={async () => {
              await expenseService.removeReceipt(expense.id);
              setReceiptVersion((v) => v + 1);
              setHasReceipt(false);
              onReceiptChanged?.();
            }}
          />
        ) : null}

        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="expenseDate" label="Date" error={errors.expenseDate?.message} required>
            <Input id="expenseDate" type="date" aria-invalid={Boolean(errors.expenseDate)}
                   {...register('expenseDate')} />
          </FormField>

          <FormField id="categoryId" label="Category" error={errors.categoryId?.message} required>
            <Controller
              control={control}
              name="categoryId"
              render={({ field }) => (
                <Select
                  value={field.value ? String(field.value) : ''}
                  onValueChange={(value) => {
                    if (value === ADD_NEW) { setAddingCategory(true); return; }
                    // Radix fires a spurious onValueChange('') right after the
                    // inline "Add new category" flow calls setValue() - the
                    // moment that commit happens, this Select's own children
                    // (built from the `categories` prop) don't yet include
                    // the just-created category, so the newly-set value has
                    // no matching SelectItem and Radix "corrects" itself back
                    // to nothing before the next render adds the missing
                    // option. No real SelectItem in this list is ever "",
                    // so an empty value here is never a legitimate user pick.
                    if (value === '') return;
                    field.onChange(Number(value));
                  }}
                >
                  <SelectTrigger id="categoryId"><SelectValue placeholder="Select a category" /></SelectTrigger>
                  <SelectContent>
                    {categories.map((option) => (
                      <SelectItem key={option.id} value={String(option.id)}>{option.name}</SelectItem>
                    ))}
                    <SelectSeparator />
                    <SelectItem value={ADD_NEW} className="font-medium text-primary">
                      <span className="flex items-center gap-1.5"><Plus className="h-3.5 w-3.5" />Add new category</span>
                    </SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>

          <FormField id="amountRupees" label="Amount (₹)" error={errors.amountRupees?.message} required>
            <Input id="amountRupees" type="number" min={0} step="0.01"
                   aria-invalid={Boolean(errors.amountRupees)} {...register('amountRupees')} />
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

          <FormField id="notes" label="Notes" error={errors.notes?.message} className="sm:col-span-2">
            <Input id="notes" aria-invalid={Boolean(errors.notes)} {...register('notes')} />
          </FormField>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
          <Button type="submit" loading={isSubmitting}>{isEdit ? 'Save changes' : 'Add expense'}</Button>
        </DialogFooter>
      </form>

      <Dialog open={addingCategory} onOpenChange={setAddingCategory}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Add expense category</DialogTitle></DialogHeader>
          <ExpenseCategoryForm onSubmit={handleCreateCategory} onCancel={() => setAddingCategory(false)} />
        </DialogContent>
      </Dialog>
    </>
  );
}
