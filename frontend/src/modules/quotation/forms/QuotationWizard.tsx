import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, ArrowRight, Check, Loader2, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { FormField } from '@/shared/components/FormField';
import { StateSelect } from '@/shared/components/StateSelect';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import type { ProductSummaryResponse } from '@/modules/product/types';
import { productService } from '@/modules/product/services/productService';
import { ProductPicker } from '@/modules/invoice/components/ProductPicker';
import {
  quotationWizardSchema, type QuotationLineDraft, type QuotationWizardValues,
} from '../validation/schemas';
import type { QuotationRequest } from '../types';

const STEPS = ['Customer', 'Items', 'Details', 'Review'] as const;

const STEP_FIELDS: Record<number, (keyof QuotationWizardValues)[]> = {
  0: ['customerName', 'customerMobile', 'customerEmail', 'customerGstNo', 'customerStateCode'],
  2: ['validUntil', 'remarks'],
};

/** Default valid-until: 7 days out, editable in step 3. */
function defaultValidUntil(): string {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  return date.toISOString().slice(0, 10);
}

/** Arriving from a customer's own page - pre-fills (never locks) the Customer step so their details never have to be re-typed (CR-030 §10). */
export interface QuotationWizardInitialCustomer {
  customerName: string;
  customerMobile: string;
  customerEmail?: string | null;
  customerGstNo?: string | null;
  customerStateCode?: string | null;
}

/** "Repeat" from a previous invoice/quotation (CR-030 §45-46) - carries the product and quantity, never the old price. */
export interface QuotationWizardInitialItem {
  productId: number;
  quantity: number;
}

interface QuotationWizardProps {
  onSubmit: (request: QuotationRequest) => Promise<void>;
  onCancel: () => void;
  initialCustomer?: QuotationWizardInitialCustomer;
  initialItems?: QuotationWizardInitialItem[];
  submitLabel?: string;
}

export function QuotationWizard({
  onSubmit, onCancel, initialCustomer, initialItems, submitLabel,
}: QuotationWizardProps) {
  const [step, setStep] = useState(0);
  const [items, setItems] = useState<QuotationLineDraft[]>([]);
  const [itemsError, setItemsError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [repeatNote, setRepeatNote] = useState<string | null>(null);

  useEffect(() => {
    if (!initialItems || initialItems.length === 0) return;
    let cancelled = false;
    Promise.allSettled(initialItems.map((line) => productService.get(line.productId).then((product) => ({ product, quantity: line.quantity }))))
      .then((results) => {
        if (cancelled) return;
        const loaded: QuotationLineDraft[] = [];
        let skipped = 0;
        for (const result of results) {
          if (result.status === 'fulfilled' && result.value.product.status === 'ACTIVE') {
            const { product, quantity } = result.value;
            loaded.push({
              lineId: nextLineId(),
              productId: product.id, productCode: product.productCode, productName: product.productName,
              unit: product.unit, sellingPriceRupees: Number(product.sellingPriceDisplay.replace(/,/g, '')), quantity,
            });
          } else {
            skipped += 1;
          }
        }
        setItems(loaded);
        if (skipped > 0) {
          setRepeatNote(`${skipped} item${skipped === 1 ? '' : 's'} from the original document ${skipped === 1 ? 'is' : 'are'} no longer available and ${skipped === 1 ? 'was' : 'were'} not re-added.`);
        }
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const {
    register, handleSubmit, trigger, watch, setValue, formState: { errors },
  } = useForm<QuotationWizardValues>({
    resolver: zodResolver(quotationWizardSchema),
    defaultValues: {
      customerName: initialCustomer?.customerName ?? '',
      customerMobile: initialCustomer?.customerMobile ?? '',
      customerEmail: initialCustomer?.customerEmail ?? '',
      customerGstNo: initialCustomer?.customerGstNo ?? '',
      customerStateCode: initialCustomer?.customerStateCode ?? '',
      validUntil: defaultValidUntil(), remarks: '',
    },
  });

  const values = watch();

  const totals = useMemo(() => {
    let subtotal = 0;
    let gst = 0;
    for (const item of items) {
      const lineSubtotal = item.sellingPriceRupees * item.quantity;
      subtotal += lineSubtotal;
      // Estimate only - the server computes the authoritative GST per line
      // from the product's own current rate at save time.
      gst += lineSubtotal * 0.18;
    }
    return { subtotal, gst, total: subtotal + gst };
  }, [items]);

  /** See InvoiceWizard.nextLineId - a line needs identity independent of its product. */
  const nextLineId = () => (
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `line-${Date.now()}-${Math.random().toString(36).slice(2)}`);

  const addItem = (product: ProductSummaryResponse) => {
    setItemsError(null);
    setItems((current) => [...current, {
      lineId: nextLineId(),
      productId: product.id,
      productCode: product.productCode,
      productName: product.productName,
      unit: product.unit,
      sellingPriceRupees: Number(product.sellingPriceDisplay.replace(/,/g, '')),
      quantity: 1,
    }]);
  };

  const updateQuantity = (lineId: string, quantity: number) => {
    setItems((current) => current.map((item) =>
      item.lineId === lineId ? { ...item, quantity } : item));
  };

  const removeItem = (lineId: string) => {
    setItems((current) => current.filter((item) => item.lineId !== lineId));
  };

  const goNext = async () => {
    const fields = STEP_FIELDS[step];
    if (fields && !(await trigger(fields))) return;
    if (step === 1) {
      if (items.length === 0) {
        setItemsError('Add at least one product before continuing.');
        return;
      }
      if (items.some((item) => !item.quantity || item.quantity <= 0)) {
        setItemsError('Every item needs a quantity greater than zero.');
        return;
      }
    }
    setItemsError(null);
    setStep((current) => Math.min(current + 1, STEPS.length - 1));
  };

  const goBack = () => setStep((current) => Math.max(current - 1, 0));

  const submit = handleSubmit(async (formValues) => {
    setFormError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        customerName: formValues.customerName,
        customerMobile: formValues.customerMobile,
        customerEmail: formValues.customerEmail || null,
        customerGstNo: formValues.customerGstNo || null,
        customerStateCode: formValues.customerStateCode || null,
        validUntil: formValues.validUntil,
        items: items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
        remarks: formValues.remarks || null,
      });
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  });

  const rupees = (value: number) => value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  return (
    <div className="flex min-h-[calc(100dvh-8.5rem)] flex-col">
      <ol className="mb-6 flex items-center gap-2 text-sm">
        {STEPS.map((label, index) => (
          <li key={label} className="flex flex-1 items-center gap-2">
            <span
              className={cn(
                'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs font-semibold',
                index < step && 'border-primary bg-primary text-primary-foreground',
                index === step && 'border-primary text-primary',
                index > step && 'border-muted-foreground/30 text-muted-foreground',
              )}
            >
              {index < step ? <Check className="h-3.5 w-3.5" /> : index + 1}
            </span>
            <span className={cn('hidden truncate sm:inline', index === step ? 'font-medium' : 'text-muted-foreground')}>
              {label}
            </span>
            {index < STEPS.length - 1 ? (
              <span className={cn('h-px flex-1', index < step ? 'bg-primary' : 'bg-border')} />
            ) : null}
          </li>
        ))}
      </ol>

      {formError ? (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      ) : null}

      {repeatNote ? (
        <Alert variant="warning" className="mb-4">
          <AlertDescription>{repeatNote}</AlertDescription>
        </Alert>
      ) : null}

      <div className="flex-1">
        {step === 0 ? (
          <div className="max-w-md space-y-4">
            <FormField id="customerName" label="Customer name" error={errors.customerName?.message} required>
              <Input id="customerName" autoFocus aria-invalid={Boolean(errors.customerName)}
                     {...register('customerName')} />
            </FormField>
            <FormField id="customerMobile" label="Mobile number" error={errors.customerMobile?.message} required
                       hint="A returning customer's existing record is reused automatically.">
              <Input id="customerMobile" inputMode="numeric" maxLength={10} placeholder="9876543210"
                     aria-invalid={Boolean(errors.customerMobile)} {...register('customerMobile')} />
            </FormField>
            <FormField id="customerEmail" label="Email (optional)" error={errors.customerEmail?.message}>
              <Input id="customerEmail" type="email" aria-invalid={Boolean(errors.customerEmail)}
                     {...register('customerEmail')} />
            </FormField>
            <FormField id="customerGstNo" label="Customer GSTIN (optional)" error={errors.customerGstNo?.message}>
              <Input id="customerGstNo" maxLength={15} className="uppercase"
                     aria-invalid={Boolean(errors.customerGstNo)} {...register('customerGstNo')} />
            </FormField>
            <FormField id="customerStateCode" label="Customer state (optional)" error={errors.customerStateCode?.message}
                       hint="Sets the GST state code, e.g. Tamil Nadu = 33.">
              <StateSelect
                id="customerStateCode"
                clearable
                value={values.customerStateCode ?? ''}
                onChange={(code) => setValue('customerStateCode', code, { shouldDirty: true, shouldValidate: true })}
              />
            </FormField>
          </div>
        ) : null}

        {step === 1 ? (
          <div className="space-y-4">
            <div className="max-w-md">
              <ProductPicker onPick={addItem} excludeIds={items.map((item) => item.productId)} />
            </div>
            {itemsError ? (
              <Alert variant="destructive"><AlertDescription>{itemsError}</AlertDescription></Alert>
            ) : null}

            {items.length === 0 ? (
              <p className="rounded-md border border-dashed py-8 text-center text-sm text-muted-foreground">
                No products added yet. Search above to add the first line item.
              </p>
            ) : (
              <div className="max-h-[45dvh] overflow-y-auto rounded-md border">
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2 font-medium">Product</th>
                      <th className="px-3 py-2 font-medium">Price</th>
                      <th className="w-28 px-3 py-2 font-medium">Qty</th>
                      <th className="px-3 py-2 text-right font-medium">Line total</th>
                      <th className="w-10 px-3 py-2" />
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {items.map((item) => (
                      <tr key={item.lineId}>
                        <td className="px-3 py-2">
                          <span className="block font-medium">{item.productName}</span>
                          <span className="block text-xs text-muted-foreground">{item.productCode}</span>
                        </td>
                        <td className="px-3 py-2 tabular">₹{rupees(item.sellingPriceRupees)} / {item.unit}</td>
                        <td className="px-3 py-2">
                          <NumberInput
                            min={0.0001} value={item.quantity}
                            onChange={(value) => updateQuantity(item.lineId, value)}
                            className="h-8"
                            aria-label={`Quantity for ${item.productName}`}
                          />
                        </td>
                        <td className="px-3 py-2 text-right tabular">
                          ₹{rupees(item.sellingPriceRupees * item.quantity)}
                        </td>
                        <td className="px-3 py-2 text-right">
                          <button
                            type="button" onClick={() => removeItem(item.lineId)}
                            className="rounded-sm p-1 text-muted-foreground hover:text-destructive"
                            aria-label={`Remove ${item.productName}`}
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {items.length > 0 ? (
              <div className="ml-auto max-w-xs space-y-1 text-sm">
                <div className="flex justify-between"><span className="text-muted-foreground">Subtotal</span><span className="tabular">₹{rupees(totals.subtotal)}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">GST (est.)</span><span className="tabular">₹{rupees(totals.gst)}</span></div>
                <div className="flex justify-between font-semibold"><span>Total (est.)</span><span className="tabular">₹{rupees(totals.total)}</span></div>
              </div>
            ) : null}
          </div>
        ) : null}

        {step === 2 ? (
          <div className="max-w-md space-y-4">
            <FormField id="validUntil" label="Valid until" error={errors.validUntil?.message} required
                       hint="After this date the quotation can no longer be converted to an invoice.">
              <Input id="validUntil" type="date" aria-invalid={Boolean(errors.validUntil)}
                     {...register('validUntil')} />
            </FormField>
            <FormField id="remarks" label="Remarks (optional)" error={errors.remarks?.message}>
              <Input id="remarks" {...register('remarks')} />
            </FormField>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="max-w-2xl space-y-5">
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Customer</p>
              <p className="font-medium">{values.customerName}</p>
              <p className="text-sm text-muted-foreground">{values.customerMobile}{values.customerEmail ? ` · ${values.customerEmail}` : ''}</p>
            </div>
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Items ({items.length})</p>
              <ul className="divide-y rounded-md border text-sm">
                {items.map((item) => (
                  <li key={item.lineId} className="flex justify-between px-3 py-2">
                    <span>{item.productName} × {item.quantity}</span>
                    <span className="tabular">₹{rupees(item.sellingPriceRupees * item.quantity)}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="ml-auto max-w-xs space-y-1 text-sm">
              <div className="flex justify-between"><span className="text-muted-foreground">Valid until</span><span>{values.validUntil}</span></div>
              <div className="flex justify-between font-semibold"><span>Total (est.)</span><span className="tabular">₹{rupees(totals.total)}</span></div>
            </div>
          </div>
        ) : null}
      </div>

      <div className="sticky bottom-0 -mx-3 mt-6 flex items-center justify-between border-t bg-background/95 px-3 py-4 backdrop-blur sm:-mx-5 sm:px-5 lg:-mx-8 lg:px-8">
        <Button type="button" variant="outline" onClick={step === 0 ? onCancel : goBack} disabled={submitting}>
          <ArrowLeft className="h-4 w-4" />
          {step === 0 ? 'Cancel' : 'Back'}
        </Button>
        {step < STEPS.length - 1 ? (
          <Button type="button" onClick={goNext}>
            Next
            <ArrowRight className="h-4 w-4" />
          </Button>
        ) : (
          <Button type="button" onClick={submit} loading={submitting}>
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            {submitLabel ?? 'Save quotation'}
          </Button>
        )}
      </div>
    </div>
  );
}
