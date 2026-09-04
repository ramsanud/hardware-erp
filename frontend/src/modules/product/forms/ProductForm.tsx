import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ImageIcon, Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
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
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PRODUCT_STATUS_OPTIONS, PRODUCT_UNIT_SUGGESTIONS } from '../constants';
import { productSchema, type ProductValues } from '../validation/schemas';
import { categoryService } from '../services/categoryService';
import { brandService } from '../services/brandService';
import { productService } from '../services/productService';
import { CategoryForm } from './CategoryForm';
import { BrandForm } from './BrandForm';
import type { BrandResponse, CategoryResponse, ProductResponse } from '../types';
import type { CategoryValues, BrandValues } from '../validation/schemas';

export const PRODUCT_FORM_ID = 'product-form';

/** Distinct from a real category/brand id so it can never collide with one (ids are positive integers). */
const ADD_NEW = '__add_new__';

interface ProductFormProps {
  /** Undefined means create. */
  product?: ProductResponse;
  categories: CategoryResponse[];
  brands: BrandResponse[];
  onSubmit: (values: ProductValues) => Promise<void>;
  onCancel: () => void;
  onDirtyChange?: (dirty: boolean) => void;
  /** Lets the caller (ProductListPage) keep its own master list/filters in sync with what was created here. */
  onCategoryCreated?: (category: CategoryResponse) => void;
  onBrandCreated?: (brand: BrandResponse) => void;
  /** Create mode only - prefills the name field, e.g. from a search query that came up empty (CR-030 §15). */
  initialProductName?: string;
  /** Edit mode only - called after the photo is uploaded/removed so the caller's own list/detail state picks up the new hasImage flag. */
  onImageChanged?: () => void;
}

const NONE = '__none__';

/**
 * One component for create and edit. purchasePriceRupees is only rendered
 * for a caller holding PRODUCT_VIEW_COST - ProductResponse.purchasePricePaise
 * comes back null without it (the backend masks cost the same way
 * SupplierResponse masks a bank account number), and the update endpoint
 * requires the field on every request. The page that opens this form is
 * responsible for not offering Edit at all to a PRODUCT_MANAGE holder who
 * lacks PRODUCT_VIEW_COST, so this form never has to silently submit a
 * fabricated price.
 */
export function ProductForm({
  product, categories, brands, onSubmit, onCancel, onDirtyChange, onCategoryCreated, onBrandCreated,
  initialProductName, onImageChanged,
}: ProductFormProps) {
  const isEdit = Boolean(product);
  const { hasPermission } = useAuth();
  const canViewCost = hasPermission(PERMISSIONS.PRODUCT_VIEW_COST);
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);
  // Nested dialogs, opened on top of this one - the product form underneath
  // never unmounts while either is open, so nothing the user already typed
  // is lost switching between them (no separate "draft" storage needed).
  const [addingCategory, setAddingCategory] = useState(false);
  const [addingBrand, setAddingBrand] = useState(false);

  // CR-036 - a photo is edit-mode only, same reasoning Shop Settings' own
  // logo upload has: there is no product id to upload against until the
  // product already exists.
  const [imageVersion, setImageVersion] = useState(0);
  const [hasImage, setHasImage] = useState(product?.hasImage ?? false);
  const imageSrc = useAuthenticatedImage(
    isEdit && hasImage && product ? productService.imageUrl(product.id) : null, imageVersion);

  const {
    register, control, handleSubmit, setError, setValue, formState: { errors, isSubmitting, isDirty },
  } = useForm<ProductValues>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      productCode: product?.productCode ?? '',
      productName: product?.productName ?? initialProductName ?? '',
      categoryId: product?.categoryId ?? null,
      brandId: product?.brandId ?? null,
      modelNo: product?.modelNo ?? '',
      manufacturerCode: product?.manufacturerCode ?? '',
      barcode: product?.barcode ?? '',
      unit: product?.unit ?? 'PCS',
      description: product?.description ?? '',
      hsnCode: product?.hsnCode ?? '',
      gstRatePercent: product?.gstRatePercent ?? 0,
      purchasePriceRupees: product?.purchasePricePaise ? product.purchasePricePaise / 100 : 0,
      sellingPriceRupees: product ? product.sellingPricePaise / 100 : 0,
      mrpRupees: product ? product.mrpPaise / 100 : 0,
      minimumStock: product?.minimumStock ?? 0,
      reorderLevel: product?.reorderLevel ?? 0,
      status: product?.status ?? 'ACTIVE',
      altUnitLabel: product?.altUnitLabel ?? '',
      altUnitConversionFactor: product?.altUnitConversionFactor ?? 0,
    },
  });

  useEffect(() => { onDirtyChange?.(isDirty); }, [isDirty, onDirtyChange]);

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof ProductValues, { message });
        });
        if (error.code === 'DUPLICATE_RESOURCE') {
          const lower = error.message.toLowerCase();
          if (lower.includes('code')) setError('productCode', { message: error.message });
          else if (lower.includes('barcode')) setError('barcode', { message: error.message });
          else if (lower.includes('name')) setError('productName', { message: error.message });
        }
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  const handleCreateCategory = async (values: CategoryValues) => {
    const created = await categoryService.create({
      categoryCode: values.categoryCode || undefined,
      categoryName: values.categoryName,
      parentCategoryId: values.parentCategoryId ?? null,
      description: values.description || null,
      status: values.status,
    });
    onCategoryCreated?.(created);
    setValue('categoryId', created.id, { shouldDirty: true, shouldValidate: true });
    setAddingCategory(false);
    toast.success(`Category "${created.categoryName}" created and selected.`);
  };

  const handleCreateBrand = async (values: BrandValues) => {
    const created = await brandService.create({
      brandCode: values.brandCode || undefined,
      brandName: values.brandName,
      description: values.description || null,
      status: values.status,
    });
    onBrandCreated?.(created);
    setValue('brandId', created.id, { shouldDirty: true, shouldValidate: true });
    setAddingBrand(false);
    toast.success(`Brand "${created.brandName}" created and selected.`);
  };

  return (
    <>
    <form id={PRODUCT_FORM_ID} onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      {isEdit && product ? (
        <ImageUpload
          src={imageSrc}
          alt={product.productName}
          shape="square"
          fallback={<ImageIcon className="h-8 w-8 text-muted-foreground" aria-hidden />}
          onUpload={async (file) => {
            await productService.uploadImage(product.id, file);
            setImageVersion((v) => v + 1);
            setHasImage(true);
            onImageChanged?.();
          }}
          onRemove={async () => {
            await productService.removeImage(product.id);
            setImageVersion((v) => v + 1);
            setHasImage(false);
            onImageChanged?.();
          }}
        />
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="productName" label="Product name" error={errors.productName?.message}
                   required className="sm:col-span-2">
          <Input id="productName" autoFocus aria-invalid={Boolean(errors.productName)}
                 {...register('productName')} />
        </FormField>

        <FormField id="productCode" label="Product code" error={errors.productCode?.message}
                   hint={isEdit ? undefined : 'Generated automatically. Only set this to match a code you already use.'}>
          <Input id="productCode" placeholder={isEdit ? undefined : 'Auto (PRD-000042)'} className="uppercase"
                 aria-invalid={Boolean(errors.productCode)} {...register('productCode')} />
        </FormField>

        <FormField id="unit" label="Unit" error={errors.unit?.message} required>
          <Input id="unit" list="product-unit-suggestions" placeholder="PCS"
                 aria-invalid={Boolean(errors.unit)} {...register('unit')} />
          <datalist id="product-unit-suggestions">
            {PRODUCT_UNIT_SUGGESTIONS.map((option) => <option key={option} value={option} />)}
          </datalist>
        </FormField>

        <FormField id="categoryId" label="Category" error={errors.categoryId?.message}>
          <Controller
            control={control}
            name="categoryId"
            render={({ field }) => (
              <Select
                value={field.value ? String(field.value) : NONE}
                onValueChange={(value) => {
                  if (value === ADD_NEW) { setAddingCategory(true); return; }
                  // Radix fires a spurious onValueChange('') right after the
                  // inline "Add new category" flow calls setValue() - at
                  // that exact commit this Select's own children (built from
                  // the `categories` prop) don't yet include the just-created
                  // category, so the newly-set value has no matching
                  // SelectItem and Radix "corrects" itself back to nothing
                  // before the next render adds the missing option. No real
                  // SelectItem here is ever "", so an empty value is never a
                  // legitimate user pick (found live testing the identical
                  // pattern in the new Expense module, CR-036).
                  if (value === '') return;
                  field.onChange(value === NONE ? null : Number(value));
                }}
              >
                <SelectTrigger id="categoryId"><SelectValue placeholder="None" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>None</SelectItem>
                  {categories.map((option) => (
                    <SelectItem key={option.id} value={String(option.id)}>{option.categoryName}</SelectItem>
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

        <FormField id="brandId" label="Brand" error={errors.brandId?.message}>
          <Controller
            control={control}
            name="brandId"
            render={({ field }) => (
              <Select
                value={field.value ? String(field.value) : NONE}
                onValueChange={(value) => {
                  if (value === ADD_NEW) { setAddingBrand(true); return; }
                  // See the matching comment on the Category Select above.
                  if (value === '') return;
                  field.onChange(value === NONE ? null : Number(value));
                }}
              >
                <SelectTrigger id="brandId"><SelectValue placeholder="None" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>None</SelectItem>
                  {brands.map((option) => (
                    <SelectItem key={option.id} value={String(option.id)}>{option.brandName}</SelectItem>
                  ))}
                  <SelectSeparator />
                  <SelectItem value={ADD_NEW} className="font-medium text-primary">
                    <span className="flex items-center gap-1.5"><Plus className="h-3.5 w-3.5" />Add new brand</span>
                  </SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </FormField>

        <FormField id="modelNo" label="Model number" error={errors.modelNo?.message}>
          <Input id="modelNo" aria-invalid={Boolean(errors.modelNo)} {...register('modelNo')} />
        </FormField>

        <FormField id="manufacturerCode" label="Manufacturer code" error={errors.manufacturerCode?.message}>
          <Input id="manufacturerCode" aria-invalid={Boolean(errors.manufacturerCode)}
                 {...register('manufacturerCode')} />
        </FormField>

        <FormField id="barcode" label="Barcode" error={errors.barcode?.message}>
          <Input id="barcode" inputMode="numeric" aria-invalid={Boolean(errors.barcode)}
                 {...register('barcode')} />
        </FormField>

        <FormField id="hsnCode" label="HSN/SAC code" error={errors.hsnCode?.message}>
          <Input id="hsnCode" aria-invalid={Boolean(errors.hsnCode)} {...register('hsnCode')} />
        </FormField>

        <FormField id="gstRatePercent" label="GST rate (%)" error={errors.gstRatePercent?.message} required>
          <Controller
            control={control}
            name="gstRatePercent"
            render={({ field }) => (
              <NumberInput id="gstRatePercent" min={0} max={100}
                           aria-invalid={Boolean(errors.gstRatePercent)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        {canViewCost ? (
          <FormField id="purchasePriceRupees" label="Purchase price (₹)"
                     error={errors.purchasePriceRupees?.message} required>
            <Controller
              control={control}
              name="purchasePriceRupees"
              render={({ field }) => (
                <NumberInput id="purchasePriceRupees" min={0}
                             aria-invalid={Boolean(errors.purchasePriceRupees)}
                             value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
              )}
            />
          </FormField>
        ) : null}

        <FormField id="sellingPriceRupees" label="Selling price (₹)"
                   error={errors.sellingPriceRupees?.message} required>
          <Controller
            control={control}
            name="sellingPriceRupees"
            render={({ field }) => (
              <NumberInput id="sellingPriceRupees" min={0}
                           aria-invalid={Boolean(errors.sellingPriceRupees)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="mrpRupees" label="MRP (₹)" error={errors.mrpRupees?.message}>
          <Controller
            control={control}
            name="mrpRupees"
            render={({ field }) => (
              <NumberInput id="mrpRupees" min={0}
                           aria-invalid={Boolean(errors.mrpRupees)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="minimumStock" label="Minimum stock" error={errors.minimumStock?.message} required>
          <Controller
            control={control}
            name="minimumStock"
            render={({ field }) => (
              <NumberInput id="minimumStock" min={0}
                           aria-invalid={Boolean(errors.minimumStock)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="reorderLevel" label="Reorder level" error={errors.reorderLevel?.message} required>
          <Controller
            control={control}
            name="reorderLevel"
            render={({ field }) => (
              <NumberInput id="reorderLevel" min={0}
                           aria-invalid={Boolean(errors.reorderLevel)}
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
                  {PRODUCT_STATUS_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>

        <FormField id="description" label="Description" error={errors.description?.message}
                   className="sm:col-span-2">
          <Input id="description" aria-invalid={Boolean(errors.description)}
                 {...register('description')} />
        </FormField>

        {/* CR-053 backlog item 1. Optional - both left blank means this
            product has no alternate unit. Printed on the invoice PDF only
            when the shop turns on "Show alternate unit" in Settings. */}
        <FormField id="altUnitLabel" label="Alternate unit label" error={errors.altUnitLabel?.message}
                   hint='e.g. "BOX" if this product is also sold by the box'>
          <Input id="altUnitLabel" placeholder="BOX" aria-invalid={Boolean(errors.altUnitLabel)}
                 {...register('altUnitLabel')} />
        </FormField>

        <FormField id="altUnitConversionFactor" label="Units per alternate unit"
                   error={errors.altUnitConversionFactor?.message}
                   hint="e.g. 12 if 1 BOX = 12 PCS">
          <Controller
            control={control}
            name="altUnitConversionFactor"
            render={({ field }) => (
              <NumberInput id="altUnitConversionFactor" min={0}
                           aria-invalid={Boolean(errors.altUnitConversionFactor)}
                           value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create product'}
        </Button>
      </DialogFooter>
    </form>

    {/* Nested on top of the product dialog, which stays mounted underneath -
        nothing already typed into the product form is lost switching here
        and back (CR-024). */}
    <Dialog open={addingCategory} onOpenChange={setAddingCategory}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>Add category</DialogTitle></DialogHeader>
        <CategoryForm categories={categories} onSubmit={handleCreateCategory}
                      onCancel={() => setAddingCategory(false)} />
      </DialogContent>
    </Dialog>

    <Dialog open={addingBrand} onOpenChange={setAddingBrand}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>Add brand</DialogTitle></DialogHeader>
        <BrandForm onSubmit={handleCreateBrand} onCancel={() => setAddingBrand(false)} />
      </DialogContent>
    </Dialog>
    </>
  );
}
