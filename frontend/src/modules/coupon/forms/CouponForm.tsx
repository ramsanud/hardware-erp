import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { Badge } from '@/shared/components/ui/badge';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { ProductPicker } from '@/modules/invoice/components/ProductPicker';
import type { ProductSummaryResponse } from '@/modules/product/types';
import { DISCOUNT_TYPE_OPTIONS, COUPON_STATUS_OPTIONS } from '../constants';
import { couponSchema, type CouponValues } from '../validation/schemas';
import type { CouponProductRef, CouponResponse } from '../types';

interface CouponFormProps {
  /** Undefined means create. */
  coupon?: CouponResponse;
  onSubmit: (values: CouponValues) => Promise<void>;
  onCancel: () => void;
}

/**
 * One form for create and edit, modelled on product/forms/CategoryForm.tsx.
 * Money and usage-limit fields go through NumberInput (never a plain
 * `<input type="number">`) so a leading zero can never leak into the value -
 * see number-input.tsx's own comment for the bug this fixes. Dates use a
 * plain `<Input type="date">` since that widget does not have the same
 * leading-zero problem.
 */
export function CouponForm({ coupon, onSubmit, onCancel }: CouponFormProps) {
  const isEdit = Boolean(coupon);
  const [formError, setFormError] = useState<string | null>(null);
  // Kept alongside the form's productIds so the picker can show names, not
  // just ids, and so a product can be removed again before submit.
  const [selectedProducts, setSelectedProducts] = useState<CouponProductRef[]>(
    coupon?.products ?? [],
  );

  const {
    register, control, handleSubmit, setError, setValue, watch,
    formState: { errors, isSubmitting },
  } = useForm<CouponValues>({
    resolver: zodResolver(couponSchema),
    defaultValues: {
      code: coupon?.code ?? '',
      description: coupon?.description ?? '',
      discountType: coupon?.discountType ?? 'PERCENT',
      discountValue: coupon?.discountValue ?? 0,
      minPurchaseRupees: coupon?.minPurchasePaise ? coupon.minPurchasePaise / 100 : 0,
      maxDiscountRupees: coupon?.maxDiscountPaise ? coupon.maxDiscountPaise / 100 : 0,
      validFrom: coupon?.validFrom ?? '',
      validUntil: coupon?.validUntil ?? '',
      usageLimit: coupon?.usageLimit ?? 0,
      status: coupon?.status ?? 'ACTIVE',
      productIds: coupon?.products.map((p) => p.id) ?? [],
    },
  });

  const discountType = watch('discountType');

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof CouponValues, { message });
        });
        if (error.code === 'DUPLICATE_RESOURCE') {
          setError('code', { message: error.message });
        }
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  const handlePickProduct = (product: ProductSummaryResponse) => {
    if (selectedProducts.some((p) => p.id === product.id)) return;
    const next = [...selectedProducts, product];
    setSelectedProducts(next);
    setValue('productIds', next.map((p) => p.id), { shouldDirty: true });
  };

  const handleRemoveProduct = (id: number) => {
    const next = selectedProducts.filter((p) => p.id !== id);
    setSelectedProducts(next);
    setValue('productIds', next.map((p) => p.id), { shouldDirty: true });
  };

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="code" label="Coupon code" error={errors.code?.message} required>
          <Input id="code" autoFocus placeholder="DIWALI10" className="uppercase"
                 aria-invalid={Boolean(errors.code)} {...register('code')} />
        </FormField>

        <FormField id="discountType" label="Discount type" error={errors.discountType?.message} required>
          <Controller
            control={control}
            name="discountType"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="discountType"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {DISCOUNT_TYPE_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>

        <FormField id="discountValue"
                   label={discountType === 'PERCENT' ? 'Discount value (%)' : 'Discount value (₹)'}
                   error={errors.discountValue?.message} required>
          <Controller
            control={control}
            name="discountValue"
            render={({ field }) => (
              <NumberInput id="discountValue" min={0} max={discountType === 'PERCENT' ? 100 : undefined}
                           aria-invalid={Boolean(errors.discountValue)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="status" label="Status" error={errors.status?.message} required>
          <Controller
            control={control}
            name="status"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="status"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {COUPON_STATUS_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>

        <FormField id="minPurchaseRupees" label="Minimum purchase (₹)" error={errors.minPurchaseRupees?.message}
                   hint="Leave at 0 for no minimum.">
          <Controller
            control={control}
            name="minPurchaseRupees"
            render={({ field }) => (
              <NumberInput id="minPurchaseRupees" min={0}
                           aria-invalid={Boolean(errors.minPurchaseRupees)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="maxDiscountRupees" label="Maximum discount (₹)" error={errors.maxDiscountRupees?.message}
                   hint="Leave at 0 for no cap.">
          <Controller
            control={control}
            name="maxDiscountRupees"
            render={({ field }) => (
              <NumberInput id="maxDiscountRupees" min={0}
                           aria-invalid={Boolean(errors.maxDiscountRupees)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="validFrom" label="Valid from" error={errors.validFrom?.message}>
          <Input id="validFrom" type="date" aria-invalid={Boolean(errors.validFrom)}
                 {...register('validFrom')} />
        </FormField>

        <FormField id="validUntil" label="Valid until" error={errors.validUntil?.message}>
          <Input id="validUntil" type="date" aria-invalid={Boolean(errors.validUntil)}
                 {...register('validUntil')} />
        </FormField>

        <FormField id="usageLimit" label="Usage limit" error={errors.usageLimit?.message}
                   hint="Leave at 0 for unlimited uses." className="sm:col-span-2">
          <Controller
            control={control}
            name="usageLimit"
            render={({ field }) => (
              <NumberInput id="usageLimit" min={0}
                           aria-invalid={Boolean(errors.usageLimit)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="description" label="Description" error={errors.description?.message}
                   className="sm:col-span-2">
          <Input id="description" aria-invalid={Boolean(errors.description)}
                 {...register('description')} />
        </FormField>
      </div>

      <FormField id="productPicker" label="Restrict to specific products"
                 hint="Leave empty to apply this coupon to every product.">
        <div className="space-y-2">
          <ProductPicker onPick={handlePickProduct} excludeIds={selectedProducts.map((p) => p.id)} />
          {selectedProducts.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {selectedProducts.map((product) => (
                <Badge key={product.id} variant="secondary" className="gap-1 pr-1">
                  {product.productName}
                  <button
                    type="button"
                    onClick={() => handleRemoveProduct(product.id)}
                    className="rounded-full p-0.5 hover:bg-background/60"
                    aria-label={`Remove ${product.productName}`}
                  >
                    <X className="h-3 w-3" />
                  </button>
                </Badge>
              ))}
            </div>
          ) : null}
        </div>
      </FormField>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create coupon'}
        </Button>
      </DialogFooter>
    </form>
  );
}
