import { z } from 'zod';

/** Step 1 - who the quote is for. */
export const quotationCustomerStepSchema = z.object({
  customerName: z.string().trim().min(1, 'Customer name is required').max(255),
  customerMobile: z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number'),
  customerEmail: z.string().trim().email('Enter a valid email address').optional().or(z.literal('')),
  customerGstNo: z.string().trim().toUpperCase()
    .regex(/^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN')
    .optional().or(z.literal('')),
  customerStateCode: z.string().trim().regex(/^\d{2}$/, 'State code is 2 digits')
    .optional().or(z.literal('')),
});

/** Step 3 - how long the quote holds, plus any note. */
export const quotationDetailsStepSchema = z.object({
  validUntil: z.string().trim().min(1, 'Choose a valid-until date')
    .refine((value) => new Date(value) > new Date(new Date().toDateString()), 'Must be a future date'),
  remarks: z.string().trim().max(500).optional().or(z.literal('')),
});

export const quotationWizardSchema = quotationCustomerStepSchema.merge(quotationDetailsStepSchema);

export type QuotationWizardValues = z.infer<typeof quotationWizardSchema>;

export interface QuotationLineDraft {
  /**
   * Stable per-LINE identity (BUG-FE-021) - see InvoiceLineDraft.lineId. The
   * same product may legitimately appear twice, and keying edits on productId
   * made every operation hit both lines.
   */
  lineId: string;
  productId: number;
  productCode: string;
  productName: string;
  unit: string;
  sellingPriceRupees: number;
  quantity: number;
}
