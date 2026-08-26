import { useEffect, useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, ArrowRight, Check, Loader2, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { StateSelect } from '@/shared/components/StateSelect';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import type { ProductSummaryResponse } from '@/modules/product/types';
import { customerService } from '@/modules/customer/services/customerService';
import type { CustomerCreditCheckResponse } from '@/modules/customer/types';
import { productService } from '@/modules/product/services/productService';
import { tenantBankAccountService } from '@/modules/settings/services/tenantBankAccountService';
import type { TenantBankAccountResponse } from '@/modules/settings/types';
import { ProductPicker } from '../components/ProductPicker';
import { CreditLimitWarning } from '../components/CreditLimitWarning';
import { PAYMENT_METHOD_OPTIONS } from '../constants';
import { invoiceWizardSchema, type InvoiceLineDraft, type InvoiceWizardValues } from '../validation/schemas';
import type { InvoiceRequest } from '../types';

const STEPS = ['Customer', 'Items', 'Payment', 'Review'] as const;

const STEP_FIELDS: Record<number, (keyof InvoiceWizardValues)[]> = {
  0: ['customerName', 'customerMobile', 'customerEmail', 'customerGstNo', 'customerStateCode'],
  2: ['initialPaymentRupees', 'paymentMethod', 'couponCode', 'remarks', 'transportMode', 'vehicleNumber', 'deliveryAddress'],
};

/** Arriving from a customer's own page - pre-fills (never locks) the Customer step so their details never have to be re-typed (CR-030 §11). */
export interface InvoiceWizardInitialCustomer {
  customerName: string;
  customerMobile: string;
  customerEmail?: string | null;
  customerGstNo?: string | null;
  customerStateCode?: string | null;
}

/** "Repeat" from a previous invoice/quotation (CR-030 §45-46) - carries the product and quantity, never the old price, so a repeat sale always reflects today's selling price. */
export interface InvoiceWizardInitialItem {
  productId: number;
  quantity: number;
}

interface InvoiceWizardProps {
  onSubmit: (request: InvoiceRequest) => Promise<void>;
  onCancel: () => void;
  initialCustomer?: InvoiceWizardInitialCustomer;
  initialItems?: InvoiceWizardInitialItem[];
  /**
   * Amending an existing unpaid invoice. The step count is unchanged - the
   * Payment step explains itself instead, because `PUT /v1/invoices/{id}`
   * deliberately does not take an initial payment and silently dropping one
   * the user had typed would be worse than not offering the field.
   */
  editing?: boolean;
  submitLabel?: string;
}

export function InvoiceWizard({
  onSubmit, onCancel, initialCustomer, initialItems, editing = false, submitLabel,
}: InvoiceWizardProps) {
  const [step, setStep] = useState(0);
  const [items, setItems] = useState<InvoiceLineDraft[]>([]);
  const [itemsError, setItemsError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [repeatNote, setRepeatNote] = useState<string | null>(null);

  // CR-036 - which of the shop's saved bank accounts (if any) to print on
  // this invoice. Not part of the validated form schema, same treatment as
  // `items` above - a plain selection, not a required field. Defaults to
  // whichever account the owner marked as default, so most invoices need no
  // action here at all.
  const [bankAccounts, setBankAccounts] = useState<TenantBankAccountResponse[]>([]);
  const [selectedBankAccountId, setSelectedBankAccountId] = useState<number | null>(null);
  const [selectedQrId, setSelectedQrId] = useState<number | null>(null);
  useEffect(() => {
    tenantBankAccountService.list()
      .then((accounts) => {
        setBankAccounts(accounts);
        const defaultAccount = accounts.find((a) => a.defaultAccount);
        if (defaultAccount) setSelectedBankAccountId(defaultAccount.id);
      })
      .catch(() => setBankAccounts([]));
  }, []);
  const selectedAccount = bankAccounts.find((a) => a.id === selectedBankAccountId) ?? null;

  useEffect(() => {
    if (!initialItems || initialItems.length === 0) return;
    let cancelled = false;
    Promise.allSettled(initialItems.map((line) => productService.get(line.productId).then((product) => ({ product, quantity: line.quantity }))))
      .then((results) => {
        if (cancelled) return;
        const loaded: InvoiceLineDraft[] = [];
        let skipped = 0;
        for (const result of results) {
          if (result.status === 'fulfilled' && result.value.product.status === 'ACTIVE') {
            const { product, quantity } = result.value;
            loaded.push({
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
    // Only ever run once, from whatever was passed on the initial mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const {
    register, control, handleSubmit, trigger, watch, setValue, formState: { errors },
  } = useForm<InvoiceWizardValues>({
    resolver: zodResolver(invoiceWizardSchema),
    defaultValues: {
      customerName: initialCustomer?.customerName ?? '',
      customerMobile: initialCustomer?.customerMobile ?? '',
      customerEmail: initialCustomer?.customerEmail ?? '',
      customerGstNo: initialCustomer?.customerGstNo ?? '',
      customerStateCode: initialCustomer?.customerStateCode ?? '',
      initialPaymentRupees: '', couponCode: '', remarks: '',
      transportMode: '', vehicleNumber: '', deliveryAddress: '',
    },
  });

  const values = watch();

  // CR-030 §17 - the wizard's customer field is free text, not a picker, so
  // this is the only way to know at Review time whether the typed mobile
  // number belongs to an existing customer with a credit limit. Purely
  // informational (the OWNER can still save past it, same as the existing
  // paymentTooHigh check does not hard-block) - it's a warning, not a rule.
  const [creditCheck, setCreditCheck] = useState<CustomerCreditCheckResponse | null>(null);
  /** Bumped after the limit is raised in place, to re-fetch and clear the warning. */
  const [creditRefresh, setCreditRefresh] = useState(0);
  useEffect(() => {
    const mobile = values.customerMobile;
    if (!mobile || mobile.length !== 10) {
      setCreditCheck(null);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      customerService.creditCheckByMobile(mobile)
        .then((result) => { if (!cancelled) setCreditCheck(result); })
        .catch(() => { if (!cancelled) setCreditCheck(null); });
    }, 400);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [values.customerMobile, creditRefresh]);

  const totals = useMemo(() => {
    let subtotal = 0;
    let gst = 0;
    for (const item of items) {
      const lineSubtotal = item.sellingPriceRupees * item.quantity;
      subtotal += lineSubtotal;
      // Estimate only, for display while editing - the server computes the
      // authoritative GST per line from the product's own rate at save time.
      gst += lineSubtotal * 0.18;
    }
    return { subtotal, gst, total: subtotal + gst };
  }, [items]);

  const initialPayment = Number(values.initialPaymentRupees) || 0;
  const paymentTooHigh = initialPayment > 0 && initialPayment > totals.total;

  // Returns the figures rather than a finished sentence, so the warning can
  // offer to raise the limit instead of only reporting the breach.
  const creditWarning = useMemo(() => {
    if (!creditCheck || creditCheck.creditLimitPaise <= 0) return null;
    const balanceDuePaise = Math.round(Math.max(totals.total - initialPayment, 0) * 100);
    const projectedPaise = creditCheck.outstandingBalancePaise + balanceDuePaise;
    if (projectedPaise <= creditCheck.creditLimitPaise) return null;
    return { check: creditCheck, projectedPaise };
  }, [creditCheck, totals.total, initialPayment]);

  const addItem = (product: ProductSummaryResponse) => {
    setItemsError(null);
    setItems((current) => [...current, {
      productId: product.id,
      productCode: product.productCode,
      productName: product.productName,
      unit: product.unit,
      sellingPriceRupees: Number(product.sellingPriceDisplay.replace(/,/g, '')),
      quantity: 1,
    }]);
  };

  const updateQuantity = (productId: number, quantity: number) => {
    setItems((current) => current.map((item) =>
      item.productId === productId ? { ...item, quantity } : item));
  };

  const removeItem = (productId: number) => {
    setItems((current) => current.filter((item) => item.productId !== productId));
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
    if (paymentTooHigh) return;
    setFormError(null);
    setSubmitting(true);
    try {
      const rupeesToPaise = (rupees: string | undefined) => {
        const parsed = Number(rupees);
        return rupees && parsed > 0 ? Math.round(parsed * 100) : null;
      };
      await onSubmit({
        customerName: formValues.customerName,
        customerMobile: formValues.customerMobile,
        customerEmail: formValues.customerEmail || null,
        customerGstNo: formValues.customerGstNo || null,
        customerStateCode: formValues.customerStateCode || null,
        items: items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
        // PUT /v1/invoices/{id} takes neither of these. Sent as null while
        // editing so the payload matches what the server will actually act on.
        initialPaymentPaise: editing ? null : rupeesToPaise(formValues.initialPaymentRupees),
        paymentMethod: editing || !rupeesToPaise(formValues.initialPaymentRupees)
          ? null : formValues.paymentMethod,
        couponCode: editing ? null : (formValues.couponCode || null),
        remarks: formValues.remarks || null,
        transportMode: formValues.transportMode || null,
        vehicleNumber: formValues.vehicleNumber || null,
        deliveryAddress: formValues.deliveryAddress || null,
        bankAccountId: selectedBankAccountId,
        bankAccountQrId: selectedQrId,
      });
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message);
        if (error.code === 'PAYMENT_EXCEEDS_TOTAL') setStep(2);
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
      {/* Step indicator - always visible, no scroll needed to see progress. */}
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

      {/* Step content. */}
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
            <FormField id="customerGstNo" label="Customer GSTIN (optional)" error={errors.customerGstNo?.message}
                       hint="Needed for a GST tax invoice. Leave blank for a regular consumer sale.">
              <Input id="customerGstNo" maxLength={15} className="uppercase"
                     aria-invalid={Boolean(errors.customerGstNo)} {...register('customerGstNo')} />
            </FormField>
            <FormField id="customerStateCode" label="Customer state (optional)" error={errors.customerStateCode?.message}
                       hint="Sets the GST state code, e.g. Tamil Nadu = 33. Determines CGST+SGST vs IGST.">
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
                      <tr key={item.productId}>
                        <td className="px-3 py-2">
                          <span className="block font-medium">{item.productName}</span>
                          <span className="block text-xs text-muted-foreground">{item.productCode}</span>
                        </td>
                        <td className="px-3 py-2 tabular">₹{rupees(item.sellingPriceRupees)} / {item.unit}</td>
                        <td className="px-3 py-2">
                          <NumberInput
                            min={0.0001} value={item.quantity}
                            onChange={(value) => updateQuantity(item.productId, value)}
                            className="h-8"
                            aria-label={`Quantity for ${item.productName}`}
                          />
                        </td>
                        <td className="px-3 py-2 text-right tabular">
                          ₹{rupees(item.sellingPriceRupees * item.quantity)}
                        </td>
                        <td className="px-3 py-2 text-right">
                          <button
                            type="button" onClick={() => removeItem(item.productId)}
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
            {editing ? (
              <Alert>
                <AlertDescription>
                  Payments are not changed here. This invoice has none recorded against
                  it yet — that is precisely why it can still be edited. Record one from
                  the invoice page after saving.
                </AlertDescription>
              </Alert>
            ) : (
              <p className="text-sm text-muted-foreground">
                Optional. Leave blank if the customer is paying nothing right now - the
                invoice will be created as Unpaid.
              </p>
            )}
            {!editing ? (
              <FormField id="initialPaymentRupees" label="Initial payment (₹)" error={errors.initialPaymentRupees?.message}>
                <Input id="initialPaymentRupees" type="number" min={0} step="0.01"
                       placeholder="0.00" {...register('initialPaymentRupees')} />
              </FormField>
            ) : null}
            {paymentTooHigh ? (
              <Alert variant="destructive">
                <AlertDescription>
                  The initial payment cannot be more than the invoice total (₹{rupees(totals.total)} est.).
                </AlertDescription>
              </Alert>
            ) : null}
            {initialPayment > 0 ? (
              <FormField id="paymentMethod" label="Payment method" error={errors.paymentMethod?.message} required>
                <Controller
                  control={control}
                  name="paymentMethod"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="paymentMethod"><SelectValue placeholder="Select a method" /></SelectTrigger>
                      <SelectContent>
                        {PAYMENT_METHOD_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </FormField>
            ) : null}
            {/* A coupon's usage count is recorded when the invoice is first
                saved, so re-applying one during an edit would either
                double-count it or silently do nothing. Not offered here. */}
            {!editing ? (
              <FormField id="couponCode" label="Coupon code (optional)" error={errors.couponCode?.message}
                         hint="Checked and applied when the invoice is saved.">
                <Input id="couponCode" className="uppercase" placeholder="SAVE10"
                       aria-invalid={Boolean(errors.couponCode)} {...register('couponCode')} />
              </FormField>
            ) : null}
            <FormField id="remarks" label="Remarks (optional)" error={errors.remarks?.message}>
              <Input id="remarks" {...register('remarks')} />
            </FormField>
            {bankAccounts.length > 0 ? (
              <>
                <p className="pt-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Payment details on the invoice (optional)
                </p>
                <FormField id="bankAccount" label="Bank account to print"
                           hint="Leave as the shop default, or pick a specific account for this invoice.">
                  <Select
                    value={selectedBankAccountId ? String(selectedBankAccountId) : 'default'}
                    onValueChange={(value) => {
                      setSelectedBankAccountId(value === 'default' ? null : Number(value));
                      setSelectedQrId(null);
                    }}
                  >
                    <SelectTrigger id="bankAccount"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="default">Shop default account</SelectItem>
                      {bankAccounts.map((account) => (
                        <SelectItem key={account.id} value={String(account.id)}>{account.label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormField>
                {selectedAccount && selectedAccount.qrCodes.length > 0 ? (
                  <FormField id="bankAccountQr" label="QR code to show"
                             hint="Leave as the first one, or pick a specific QR for this invoice.">
                    <Select
                      value={selectedQrId ? String(selectedQrId) : 'first'}
                      onValueChange={(value) => setSelectedQrId(value === 'first' ? null : Number(value))}
                    >
                      <SelectTrigger id="bankAccountQr"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="first">{selectedAccount.qrCodes[0].label} (default)</SelectItem>
                        {selectedAccount.qrCodes.slice(1).map((qr) => (
                          <SelectItem key={qr.id} value={String(qr.id)}>{qr.label}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormField>
                ) : null}
              </>
            ) : null}
            <p className="pt-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Shipment details (optional)
            </p>
            <FormField id="transportMode" label="Transport mode" error={errors.transportMode?.message}>
              <Input id="transportMode" placeholder="By Road" {...register('transportMode')} />
            </FormField>
            <FormField id="vehicleNumber" label="Vehicle number" error={errors.vehicleNumber?.message}>
              <Input id="vehicleNumber" placeholder="KA01AB1234" {...register('vehicleNumber')} />
            </FormField>
            <FormField id="deliveryAddress" label="Delivery address" error={errors.deliveryAddress?.message}>
              <Input id="deliveryAddress" {...register('deliveryAddress')} />
            </FormField>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="max-w-2xl space-y-5">
            {creditWarning ? (
              <CreditLimitWarning
                check={creditWarning.check}
                projectedPaise={creditWarning.projectedPaise}
                onLimitChanged={() => setCreditRefresh((n) => n + 1)}
              />
            ) : null}
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Customer</p>
              <p className="font-medium">{values.customerName}</p>
              <p className="text-sm text-muted-foreground">{values.customerMobile}{values.customerEmail ? ` · ${values.customerEmail}` : ''}</p>
            </div>
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Items ({items.length})</p>
              <ul className="divide-y rounded-md border text-sm">
                {items.map((item) => (
                  <li key={item.productId} className="flex justify-between px-3 py-2">
                    <span>{item.productName} × {item.quantity}</span>
                    <span className="tabular">₹{rupees(item.sellingPriceRupees * item.quantity)}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="ml-auto max-w-xs space-y-1 text-sm">
              <div className="flex justify-between"><span className="text-muted-foreground">Total (est.)</span><span className="tabular font-semibold">₹{rupees(totals.total)}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">Initial payment</span><span className="tabular">₹{rupees(initialPayment)}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">Balance due (est.)</span><span className="tabular">₹{rupees(Math.max(totals.total - initialPayment, 0))}</span></div>
            </div>
          </div>
        ) : null}
      </div>

      {/* Fixed navigation - stays visible without scrolling to find it. */}
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
          <Button type="button" onClick={submit} loading={submitting} disabled={paymentTooHigh}>
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            {submitLabel ?? 'Save Invoice'}
          </Button>
        )}
      </div>
    </div>
  );
}
