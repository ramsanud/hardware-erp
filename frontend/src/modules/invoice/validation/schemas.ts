import { z } from 'zod';
import type { LineDiscountType } from '../types';

/** Step 1 - who the buyer is. */
export const customerStepSchema = z.object({
  customerName: z.string().trim().min(1, 'Customer name is required').max(255),
  customerMobile: z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number'),
  customerEmail: z.string().trim().email('Enter a valid email address').optional().or(z.literal('')),
  customerGstNo: z.string().trim().toUpperCase()
    .regex(/^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN')
    .optional().or(z.literal('')),
  customerStateCode: z.string().trim().regex(/^\d{2}$/, 'State code is 2 digits')
    .optional().or(z.literal('')),
});

/** Step 3 - the optional initial payment, plus optional shipment details for the PDF. */
export const paymentStepSchema = z.object({
  initialPaymentRupees: z.string().trim().optional().or(z.literal('')),
  paymentMethod: z.enum(['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER']).optional(),
  couponCode: z.string().trim().toUpperCase().max(30).optional().or(z.literal('')),
  remarks: z.string().trim().max(500).optional().or(z.literal('')),
  transportMode: z.string().trim().max(50).optional().or(z.literal('')),
  vehicleNumber: z.string().trim().max(20).optional().or(z.literal('')),
  deliveryAddress: z.string().trim().max(500).optional().or(z.literal('')),
});

export const invoiceWizardSchema = customerStepSchema.merge(paymentStepSchema);

export type InvoiceWizardValues = z.infer<typeof invoiceWizardSchema>;

export interface InvoiceLineDraft {
  productId: number;
  productCode: string;
  productName: string;
  unit: string;
  sellingPriceRupees: number;
  quantity: number;
  /**
   * CR-047. Held in RUPEES here because that is what the owner types; the
   * wizard converts to paise on submit. Every figure shown while editing is
   * an estimate - the backend recomputes and is authoritative.
   */
  discountType: LineDiscountType;
  discountPercent: number;
  discountAmountRupees: number;
}

/**
 * Gross, discount and net for one draft line, in rupees. Mirrors the order
 * the backend's LineDiscount applies them, so the preview and the saved
 * document agree: discount off gross first, GST on the remainder.
 */
export function priceDraftLine(item: InvoiceLineDraft): {
  gross: number; discount: number; net: number;
} {
  const gross = item.sellingPriceRupees * item.quantity;
  let discount = 0;
  if (item.discountType === 'PERCENTAGE') {
    discount = (gross * (item.discountPercent || 0)) / 100;
  } else if (item.discountType === 'AMOUNT') {
    discount = item.discountAmountRupees || 0;
  }
  // Clamp only for DISPLAY. The backend rejects an over-large discount
  // outright rather than clamping, and that rejection is what the owner must
  // see - this just stops the preview flashing a negative total mid-typing.
  discount = Math.min(Math.max(discount, 0), gross);
  return { gross, discount, net: gross - discount };
}
