import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { ProductPicker } from '@/modules/invoice/components/ProductPicker';
import { SupplierPicker } from '../components/SupplierPicker';
import { projectMaterialSchema, type ProjectMaterialValues } from '../validation/schemas';
import type { ProjectMaterialRequest } from '../types';
import type { ProductSummaryResponse } from '@/modules/product/types';
import type { SupplierSummaryResponse } from '@/modules/supplier/types';

interface ProjectMaterialFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (request: ProjectMaterialRequest) => Promise<void>;
}

/**
 * Product is required, supplier is deliberately optional (request §9-10) -
 * "Add product" if it doesn't exist yet is satisfied by ProductPicker's own
 * excludeIds-free reuse here; a genuinely missing product is created from
 * the Products page in another tab and this dialog re-searched, since
 * this module doesn't duplicate Product's own create form.
 */
export function ProjectMaterialFormDialog({ open, onOpenChange, onSubmit }: ProjectMaterialFormDialogProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const [product, setProduct] = useState<ProductSummaryResponse | null>(null);
  const [supplier, setSupplier] = useState<SupplierSummaryResponse | null>(null);

  const {
    control, handleSubmit, register, setValue, reset, formState: { errors, isSubmitting },
  } = useForm<ProjectMaterialValues>({
    resolver: zodResolver(projectMaterialSchema),
    defaultValues: { productId: 0, quantityWastage: 0, notes: '' },
  });

  const close = () => {
    reset({ productId: 0, quantityWastage: 0, notes: '' });
    setProduct(null);
    setSupplier(null);
    setFormError(null);
    onOpenChange(false);
  };

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit({
        productId: values.productId,
        supplierId: values.supplierId ?? null,
        quantityRequired: values.quantityRequired ?? null,
        quantityEstimated: values.quantityEstimated ?? null,
        quantityActual: values.quantityActual ?? null,
        quantityWastage: values.quantityWastage ?? null,
        unitPricePaise: values.unitPriceRupees != null ? Math.round(values.unitPriceRupees * 100) : null,
        notes: values.notes || null,
      });
      close();
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.');
    }
  });

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!isSubmitting) { if (!next) close(); else onOpenChange(next); } }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader><DialogTitle>Add material</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}

          <FormField id="product" label="Product" error={errors.productId?.message} required>
            {product ? (
              <div className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                <span><span className="font-medium">{product.productName}</span> <span className="text-muted-foreground">{product.productCode}</span></span>
                <button type="button" onClick={() => { setProduct(null); setValue('productId', 0); }} aria-label="Change product">
                  <X className="h-4 w-4 text-muted-foreground hover:text-foreground" />
                </button>
              </div>
            ) : (
              <ProductPicker onPick={(p) => {
                setProduct(p);
                setValue('productId', p.id, { shouldValidate: true });
                setValue('unitPriceRupees', Number(p.sellingPriceDisplay.replace(/,/g, '')));
              }} excludeIds={[]} />
            )}
          </FormField>

          <FormField id="supplier" label="Supplier (optional)" hint="Leave blank for old stock with no recorded supplier.">
            {supplier ? (
              <div className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                <span className="font-medium">{supplier.supplierName}</span>
                <button type="button" onClick={() => { setSupplier(null); setValue('supplierId', null); }} aria-label="Remove supplier">
                  <X className="h-4 w-4 text-muted-foreground hover:text-foreground" />
                </button>
              </div>
            ) : (
              <SupplierPicker onPick={(s) => { setSupplier(s); setValue('supplierId', s.id); }} />
            )}
          </FormField>

          <div className="grid grid-cols-2 gap-4">
            <FormField id="quantityRequired" label="Qty required">
              <Controller control={control} name="quantityRequired" render={({ field }) => (
                <NumberInput id="quantityRequired" min={0} value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="quantityActual" label="Qty actual used">
              <Controller control={control} name="quantityActual" render={({ field }) => (
                <NumberInput id="quantityActual" min={0} value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="quantityWastage" label="Wastage">
              <Controller control={control} name="quantityWastage" render={({ field }) => (
                <NumberInput id="quantityWastage" min={0} value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
            <FormField id="unitPriceRupees" label="Unit price (₹)" hint="Defaults to the product's selling price.">
              <Controller control={control} name="unitPriceRupees" render={({ field }) => (
                <NumberInput id="unitPriceRupees" min={0} value={field.value ?? 0} onChange={field.onChange} onBlur={field.onBlur} />
              )} />
            </FormField>
          </div>

          <FormField id="materialNotes" label="Notes (optional)">
            <Input id="materialNotes" {...register('notes')} />
          </FormField>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={close} disabled={isSubmitting}>Cancel</Button>
            <Button type="submit" loading={isSubmitting}>Add material</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
