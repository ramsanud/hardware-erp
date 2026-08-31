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
  /**
   * Stable per-LINE identity (BUG-FE-021).
   *
   * productId cannot serve as the key: the same product may legitimately
   * appear on two lines - different quantities, different discounts, a
   * split delivery - and keying edits on productId then made every
   * operation hit both lines at once. Editing one line's labour changed
   * both; deleting one deleted both; and React saw duplicate keys, which
   * corrupts reconciliation independently of any of that.
   */
  lineId: string;
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
  /**
   * CR-050 internal labour margin, as a percentage of the DISCOUNTED value.
   * Owner-only: it raises the rate the customer is charged but never appears
   * as its own line on a customer document.
   */
  labourPercent: number;
}

/**
 * Gross, discount and net for one draft line, in rupees. Mirrors the order
 * the backend's LineDiscount applies them, so the preview and the saved
 * document agree: discount off gross first, GST on the remainder.
 */
export function priceDraftLine(item: InvoiceLineDraft): {
  gross: number; discount: number; labour: number; net: number; effectiveUnit: number;
} {
  const gross = item.sellingPriceRupees * item.quantity;
  // CR-050: percentage only.
  const discountPct = item.discountType === 'PERCENTAGE' ? (item.discountPercent || 0) : 0;
  let discount = (gross * discountPct) / 100;
  // Clamp only for DISPLAY. The backend rejects an over-large discount
  // outright rather than clamping, and that rejection is what the owner must
  // see - this just stops the preview flashing a negative total mid-typing.
  discount = Math.min(Math.max(discount, 0), gross);

  // CR-050 order: labour is a percentage of the value AFTER discount, never
  // of the gross. Taking it off the gross would quietly hand back part of
  // the discount, and the preview would then disagree with the saved
  // document. Mirrors LineDiscount.price on the backend exactly.
  const afterDiscount = gross - discount;
  const labour = (afterDiscount * Math.min(Math.max(item.labourPercent || 0, 0), 100)) / 100;
  const net = afterDiscount + labour;

  return {
    gross,
    discount,
    labour,
    net,
    effectiveUnit: item.quantity > 0 ? net / item.quantity : 0,
  };
}
