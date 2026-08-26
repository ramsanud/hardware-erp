import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Check, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Card, CardContent } from '@/shared/components/ui/card';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { FormField } from '@/shared/components/FormField';
import { ProductPicker } from '@/modules/invoice/components/ProductPicker';
import { SupplierPicker } from '@/modules/project/components/SupplierPicker';
import type { SupplierSummaryResponse } from '@/modules/supplier/types';
import type { ProductSummaryResponse } from '@/modules/product/types';
import { PAYMENT_METHOD_OPTIONS } from '../constants';
import type { PurchaseRequest } from '../types';

interface LineDraft {
  productId: number;
  productName: string;
  unit: string;
  quantity: number;
  unitPriceRupees: number;
  gstRatePercent: number;
}

interface PurchaseFormProps {
  onSubmit: (request: PurchaseRequest) => Promise<void>;
  onCancel: () => void;
}

const schema = z.object({
  supplierBillNumber: z.string().trim().max(60).optional().or(z.literal('')),
  purchaseDate: z.string().min(1, 'Purchase date is required'),
  updateProductCost: z.boolean(),
  initialPaymentRupees: z.string().trim().optional().or(z.literal('')),
  paymentMethod: z.enum(['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER']).optional(),
  remarks: z.string().trim().max(500).optional().or(z.literal('')),
});
type FormValues = z.infer<typeof schema>;

export function PurchaseForm({ onSubmit, onCancel }: PurchaseFormProps) {
  const [supplier, setSupplier] = useState<SupplierSummaryResponse | null>(null);
  const [items, setItems] = useState<LineDraft[]>([]);
  const [itemsError, setItemsError] = useState<string | null>(null);
  const [supplierError, setSupplierError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      supplierBillNumber: '', purchaseDate: new Date().toISOString().slice(0, 10),
      updateProductCost: true, initialPaymentRupees: '', paymentMethod: 'CASH', remarks: '',
    },
  });

  const addProduct = (product: ProductSummaryResponse) => {
    if (items.some((item) => item.productId === product.id)) return;
    setItems((current) => [...current, {
      productId: product.id, productName: product.productName, unit: product.unit,
      quantity: 1, unitPriceRupees: 0, gstRatePercent: Number(product.gstRatePercent) || 0,
    }]);
    setItemsError(null);
  };

  const updateItem = (productId: number, patch: Partial<LineDraft>) => {
    setItems((current) => current.map((item) => (item.productId === productId ? { ...item, ...patch } : item)));
  };

  const removeItem = (productId: number) => {
    setItems((current) => current.filter((item) => item.productId !== productId));
  };

  const subtotalPaise = items.reduce((sum, item) => {
    const price = Math.round(item.unitPriceRupees * 100);
    return sum + item.quantity * price;
  }, 0);
  const gstPaise = items.reduce((sum, item) => {
    const price = Math.round(item.unitPriceRupees * 100);
    return sum + Math.round(item.quantity * price * (item.gstRatePercent / 100));
  }, 0);

  const submit = handleSubmit(async (values) => {
    if (!supplier) { setSupplierError('Choose a supplier'); return; }
    if (items.length === 0) { setItemsError('Add at least one product'); return; }
    const invalidLine = items.find((item) => item.quantity <= 0 || item.unitPriceRupees < 0);
    if (invalidLine) { setItemsError(`Enter a valid quantity and price for ${invalidLine.productName}`); return; }

    const initialPaymentRupees = values.initialPaymentRupees ? Number(values.initialPaymentRupees) : 0;

    await onSubmit({
      supplierId: supplier.id,
      supplierBillNumber: values.supplierBillNumber || null,
      purchaseDate: values.purchaseDate,
      items: items.map((item) => ({
        productId: item.productId,
        quantity: item.quantity,
        unitPricePaise: Math.round(item.unitPriceRupees * 100),
        gstRatePercent: item.gstRatePercent,
      })),
      updateProductCost: values.updateProductCost,
      initialPaymentPaise: initialPaymentRupees > 0 ? Math.round(initialPaymentRupees * 100) : null,
      paymentMethod: initialPaymentRupees > 0 ? values.paymentMethod ?? 'CASH' : null,
      remarks: values.remarks || null,
    });
  });

  return (
    <form onSubmit={submit} className="space-y-5" noValidate>
      <Card>
        <CardContent className="grid gap-4 p-5 sm:grid-cols-2">
          <div>
            <p className="mb-1.5 text-sm font-medium">Supplier <span className="text-destructive">*</span></p>
            {supplier ? (
              <div className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
                <span>
                  <span className="font-medium">{supplier.supplierName}</span>
                  <span className="ml-2 text-xs text-muted-foreground">{supplier.supplierCode}</span>
                </span>
                <Button type="button" variant="ghost" size="sm" onClick={() => setSupplier(null)}>Change</Button>
              </div>
            ) : (
              <SupplierPicker onPick={(picked) => { setSupplier(picked); setSupplierError(null); }} />
            )}
            {supplierError ? <p className="mt-1 text-sm text-destructive">{supplierError}</p> : null}
          </div>
          <FormField id="supplierBillNumber" label="Supplier bill number" error={errors.supplierBillNumber?.message}>
            <Input id="supplierBillNumber" placeholder="e.g. 1045" {...register('supplierBillNumber')} />
          </FormField>
          <FormField id="purchaseDate" label="Purchase date" error={errors.purchaseDate?.message} required>
            <Input id="purchaseDate" type="date" {...register('purchaseDate')} />
          </FormField>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3 p-5">
          <p className="text-sm font-medium">Products <span className="text-destructive">*</span></p>
          <ProductPicker onPick={addProduct} excludeIds={items.map((item) => item.productId)} />
          {itemsError ? <p className="text-sm text-destructive">{itemsError}</p> : null}

          {items.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead className="w-24">Qty</TableHead>
                  <TableHead className="w-32">Unit price (₹)</TableHead>
                  <TableHead className="w-24">GST %</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.productId}>
                    <TableCell className="font-medium">{item.productName} <span className="text-xs text-muted-foreground">({item.unit})</span></TableCell>
                    <TableCell>
                      <NumberInput value={item.quantity} onChange={(value) => updateItem(item.productId, { quantity: value })} />
                    </TableCell>
                    <TableCell>
                      <NumberInput value={item.unitPriceRupees} onChange={(value) => updateItem(item.productId, { unitPriceRupees: value })} />
                    </TableCell>
                    <TableCell>
                      <NumberInput value={item.gstRatePercent} onChange={(value) => updateItem(item.productId, { gstRatePercent: value })} />
                    </TableCell>
                    <TableCell>
                      <Button type="button" variant="ghost" size="icon" onClick={() => removeItem(item.productId)}>
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <p className="py-6 text-center text-sm text-muted-foreground">No products added yet.</p>
          )}

          {items.length > 0 ? (
            <div className="flex justify-end gap-6 border-t pt-3 text-sm">
              <span>Subtotal: <span className="tabular font-medium">₹{(subtotalPaise / 100).toFixed(2)}</span></span>
              <span>GST: <span className="tabular font-medium">₹{(gstPaise / 100).toFixed(2)}</span></span>
              <span>Total: <span className="tabular font-semibold">₹{((subtotalPaise + gstPaise) / 100).toFixed(2)}</span></span>
            </div>
          ) : null}

          <label className="flex items-center gap-2 pt-2 text-sm">
            <input type="checkbox" className="h-4 w-4 rounded border-input" {...register('updateProductCost')} />
            Update each product's purchase price to what's paid on this bill
          </label>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="grid gap-4 p-5 sm:grid-cols-3">
          <FormField id="initialPaymentRupees" label="Paid now (₹, optional)" error={errors.initialPaymentRupees?.message}>
            <Input id="initialPaymentRupees" type="number" min={0} step="0.01" {...register('initialPaymentRupees')} />
          </FormField>
          <FormField id="paymentMethod" label="Payment method" error={errors.paymentMethod?.message}>
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
          <FormField id="remarks" label="Remarks (optional)" error={errors.remarks?.message}>
            <Input id="remarks" {...register('remarks')} />
          </FormField>
        </CardContent>
      </Card>

      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>
          <Check className="h-4 w-4" />
          Save purchase
        </Button>
      </div>
    </form>
  );
}
