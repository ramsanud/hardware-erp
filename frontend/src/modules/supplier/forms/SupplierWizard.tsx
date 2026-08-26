import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, ArrowRight, Check, Loader2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { cn } from '@/shared/lib/utils';
import { ApiError } from '@/shared/types/api';
import { INDIAN_STATES } from '@/shared/data/indianStates';
import { BANK_OTHER, INDIAN_BANKS } from '@/shared/data/indianBanks';
import { SUPPLIER_STATUS_OPTIONS } from '../constants';
import { supplierSchema, type SupplierValues } from '../validation/schemas';
import type { SupplierResponse } from '../types';

const STEPS = ['Basic Information', 'Contact Information', 'Address', 'Bank / Financial', 'Review & Save'] as const;

const STEP_FIELDS: Record<number, (keyof SupplierValues)[]> = {
  0: ['supplierName', 'supplierCode', 'status'],
  1: ['contactPerson', 'mobileNo', 'alternateMobileNo', 'email'],
  2: ['addressLine1', 'addressLine2', 'city', 'stateCode', 'pincode'],
  3: ['paymentTermsDays', 'creditLimitRupees', 'bankAccountName', 'bankAccountNo', 'bankIfsc', 'bankName'],
};

interface SupplierWizardProps {
  /** Undefined means create. */
  supplier?: SupplierResponse;
  onSubmit: (values: SupplierValues) => Promise<void>;
  onCancel: () => void;
}

/**
 * Replaces the single flat 20-field dialog. Same field set, same
 * validation, same toSupplierRequest mapping at the call site - only the
 * presentation changed, from one long scroll to five short steps with a
 * fixed Back/Next bar that never scrolls out of view.
 */
export function SupplierWizard({ supplier, onSubmit, onCancel }: SupplierWizardProps) {
  const isEdit = Boolean(supplier);
  const [step, setStep] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const {
    register, control, handleSubmit, trigger, setError, watch, setValue, formState: { errors },
  } = useForm<SupplierValues>({
    resolver: zodResolver(supplierSchema),
    defaultValues: {
      supplierCode: supplier?.supplierCode ?? '',
      supplierName: supplier?.supplierName ?? '',
      contactPerson: supplier?.contactPerson ?? '',
      mobileNo: supplier?.mobileNo ?? '',
      alternateMobileNo: supplier?.alternateMobileNo ?? '',
      email: supplier?.email ?? '',
      gstNo: supplier?.gstNo ?? '',
      panNo: supplier?.panNo ?? '',
      addressLine1: supplier?.addressLine1 ?? '',
      addressLine2: supplier?.addressLine2 ?? '',
      city: supplier?.city ?? '',
      stateCode: supplier?.stateCode ?? '',
      pincode: supplier?.pincode ?? '',
      paymentTermsDays: supplier?.paymentTermsDays ?? 30,
      creditLimitRupees: supplier ? supplier.creditLimitPaise / 100 : 0,
      bankAccountName: supplier?.bankAccountName ?? '',
      bankAccountNo: '',
      bankIfsc: supplier?.bankIfsc ?? '',
      bankName: supplier?.bankName ?? '',
      status: supplier?.status ?? 'ACTIVE',
      remarks: supplier?.remarks ?? '',
    },
  });

  const values = watch();

  const goNext = async () => {
    const fields = STEP_FIELDS[step];
    if (fields && !(await trigger(fields))) return;
    setStep((current) => Math.min(current + 1, STEPS.length - 1));
  };

  const goBack = () => setStep((current) => Math.max(current - 1, 0));

  const submit = handleSubmit(async (formValues) => {
    setFormError(null);
    setSubmitting(true);
    try {
      await onSubmit(formValues);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof SupplierValues, { message });
        });
        if (error.code === 'DUPLICATE_RESOURCE') {
          const lower = error.message.toLowerCase();
          if (lower.includes('code')) { setError('supplierCode', { message: error.message }); setStep(0); }
          else if (lower.includes('gst')) { setError('gstNo', { message: error.message }); setStep(2); }
          else if (lower.includes('name')) { setError('supplierName', { message: error.message }); setStep(0); }
        }
        if (error.code === 'BUSINESS_RULE_VIOLATION') {
          setError('gstNo', { message: error.message });
          setError('stateCode', { message: error.message });
          setStep(2);
        }
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  });

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

      <div className="flex-1">
        {step === 0 ? (
          <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
            <FormField id="supplierName" label="Supplier Shop Name" error={errors.supplierName?.message}
                       required className="sm:col-span-2">
              <Input id="supplierName" autoFocus aria-invalid={Boolean(errors.supplierName)}
                     {...register('supplierName')} />
            </FormField>
            <FormField id="supplierCode" label="Supplier code" error={errors.supplierCode?.message}
                       hint={isEdit ? undefined : 'Leave blank to auto-generate, e.g. SUP-0007.'}>
              <Input id="supplierCode" placeholder="SUP-0007"
                     aria-invalid={Boolean(errors.supplierCode)} {...register('supplierCode')} />
            </FormField>
            <FormField id="status" label="Status" error={errors.status?.message} required>
              <Controller
                control={control}
                name="status"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="status"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {SUPPLIER_STATUS_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </FormField>
          </div>
        ) : null}

        {step === 1 ? (
          <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
            <FormField id="contactPerson" label="Contact Person Name" error={errors.contactPerson?.message}>
              <Input id="contactPerson" autoFocus aria-invalid={Boolean(errors.contactPerson)}
                     {...register('contactPerson')} />
            </FormField>
            <FormField id="mobileNo" label="Mobile number" error={errors.mobileNo?.message} required>
              <Input id="mobileNo" inputMode="numeric" maxLength={10} placeholder="9876543210"
                     aria-invalid={Boolean(errors.mobileNo)} {...register('mobileNo')} />
            </FormField>
            <FormField id="alternateMobileNo" label="Alternate mobile" error={errors.alternateMobileNo?.message}>
              <Input id="alternateMobileNo" inputMode="numeric" maxLength={10}
                     aria-invalid={Boolean(errors.alternateMobileNo)} {...register('alternateMobileNo')} />
            </FormField>
            <FormField id="email" label="Email" error={errors.email?.message}>
              <Input id="email" type="email" aria-invalid={Boolean(errors.email)} {...register('email')} />
            </FormField>
          </div>
        ) : null}

        {step === 2 ? (
          <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
            <FormField id="addressLine1" label="Address line 1" error={errors.addressLine1?.message}
                       className="sm:col-span-2">
              <Input id="addressLine1" autoFocus aria-invalid={Boolean(errors.addressLine1)}
                     {...register('addressLine1')} />
            </FormField>
            <FormField id="addressLine2" label="Address line 2" error={errors.addressLine2?.message}
                       className="sm:col-span-2">
              <Input id="addressLine2" aria-invalid={Boolean(errors.addressLine2)} {...register('addressLine2')} />
            </FormField>
            <FormField id="city" label="City" error={errors.city?.message}>
              <Input id="city" aria-invalid={Boolean(errors.city)} {...register('city')} />
            </FormField>
            <FormField id="pincode" label="Pincode" error={errors.pincode?.message}>
              <Input id="pincode" inputMode="numeric" maxLength={6}
                     aria-invalid={Boolean(errors.pincode)} {...register('pincode')} />
            </FormField>
            <FormField id="gstNo" label="GSTIN" error={errors.gstNo?.message}>
              <Input id="gstNo" placeholder="33AABCS1429B1ZP" className="uppercase"
                     aria-invalid={Boolean(errors.gstNo)} {...register('gstNo')} />
            </FormField>
            <FormField id="stateName" label="State" hint="Selecting a state fills in its GST code automatically.">
              <Select
                value={INDIAN_STATES.find((s) => s.code === values.stateCode)?.name ?? ''}
                onValueChange={(name) => {
                  const state = INDIAN_STATES.find((s) => s.name === name);
                  if (state) setValue('stateCode', state.code, { shouldDirty: true, shouldValidate: true });
                }}
              >
                <SelectTrigger id="stateName"><SelectValue placeholder="Select a state" /></SelectTrigger>
                <SelectContent>
                  {INDIAN_STATES.map((state) => (
                    <SelectItem key={state.code} value={state.name}>{state.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
            <FormField id="stateCode" label="GST state code" error={errors.stateCode?.message}
                       hint="Two digits, e.g. 33 for Tamil Nadu. Auto-filled above, or enter directly.">
              <Input id="stateCode" inputMode="numeric" maxLength={2}
                     aria-invalid={Boolean(errors.stateCode)} {...register('stateCode')} />
            </FormField>
            <FormField id="panNo" label="PAN" error={errors.panNo?.message}>
              <Input id="panNo" placeholder="AABCS1429B" className="uppercase"
                     aria-invalid={Boolean(errors.panNo)} {...register('panNo')} />
            </FormField>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
            <FormField id="paymentTermsDays" label="Payment terms (days)" error={errors.paymentTermsDays?.message}
                       required hint="0 means cash on delivery.">
              <Controller
                control={control}
                name="paymentTermsDays"
                render={({ field }) => (
                  <NumberInput id="paymentTermsDays" autoFocus min={0} max={365}
                               aria-invalid={Boolean(errors.paymentTermsDays)}
                               value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
                )}
              />
            </FormField>
            <FormField id="creditLimitRupees" label="Credit limit (₹)" error={errors.creditLimitRupees?.message} required>
              <Controller
                control={control}
                name="creditLimitRupees"
                render={({ field }) => (
                  <NumberInput id="creditLimitRupees" min={0}
                               aria-invalid={Boolean(errors.creditLimitRupees)}
                               value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
                )}
              />
            </FormField>
            <FormField id="bankAccountName" label="Bank account name" error={errors.bankAccountName?.message}>
              <Input id="bankAccountName" aria-invalid={Boolean(errors.bankAccountName)}
                     {...register('bankAccountName')} />
            </FormField>
            <FormField
              id="bankAccountNo" label="Bank account number" error={errors.bankAccountNo?.message}
              hint={supplier?.bankAccountNo
                ? `Currently ${supplier.bankAccountNo}. Leave blank to clear it, or enter the full number to replace it.`
                : undefined}
            >
              <Input id="bankAccountNo" aria-invalid={Boolean(errors.bankAccountNo)} {...register('bankAccountNo')} />
            </FormField>
            <FormField id="bankIfsc" label="IFSC code" error={errors.bankIfsc?.message}>
              <Input id="bankIfsc" placeholder="HDFC0001234" className="uppercase"
                     aria-invalid={Boolean(errors.bankIfsc)} {...register('bankIfsc')} />
            </FormField>
            <FormField id="bankName" label="Bank name" error={errors.bankName?.message}>
              <Select
                value={INDIAN_BANKS.includes(values.bankName ?? '') ? values.bankName
                  : (values.bankName ? BANK_OTHER : '')}
                onValueChange={(bank) => setValue('bankName', bank === BANK_OTHER ? '' : bank,
                  { shouldDirty: true, shouldValidate: true })}
              >
                <SelectTrigger id="bankName"><SelectValue placeholder="Select a bank" /></SelectTrigger>
                <SelectContent>
                  {INDIAN_BANKS.map((bank) => <SelectItem key={bank} value={bank}>{bank}</SelectItem>)}
                  <SelectItem value={BANK_OTHER}>Other</SelectItem>
                </SelectContent>
              </Select>
              {!INDIAN_BANKS.includes(values.bankName ?? '') ? (
                <Input className="mt-2" placeholder="Enter bank name" aria-invalid={Boolean(errors.bankName)}
                       {...register('bankName')} />
              ) : null}
            </FormField>
            <FormField id="remarks" label="Remarks (optional)" error={errors.remarks?.message} className="sm:col-span-2">
              <Input id="remarks" aria-invalid={Boolean(errors.remarks)} {...register('remarks')} />
            </FormField>
          </div>
        ) : null}

        {step === 4 ? (
          <div className="max-w-2xl space-y-5">
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Basic Information</p>
              <p className="font-medium">{values.supplierName}</p>
              <p className="text-sm text-muted-foreground">{values.supplierCode || 'Auto-generated code'} · {values.status}</p>
            </div>
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Contact</p>
              <p className="text-sm">{values.contactPerson || '—'} · {values.mobileNo}{values.email ? ` · ${values.email}` : ''}</p>
            </div>
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Address</p>
              <p className="text-sm">
                {[values.addressLine1, values.addressLine2, values.city, values.pincode].filter(Boolean).join(', ') || '—'}
              </p>
              {values.gstNo ? <p className="text-sm text-muted-foreground">GSTIN {values.gstNo}</p> : null}
            </div>
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Bank / Financial</p>
              <p className="text-sm">Payment terms: {values.paymentTermsDays} days · Credit limit: ₹{values.creditLimitRupees}</p>
              {values.bankName ? <p className="text-sm text-muted-foreground">{values.bankName} {values.bankIfsc}</p> : null}
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
            {isEdit ? 'Save changes' : 'Save Supplier'}
          </Button>
        )}
      </div>
    </div>
  );
}
