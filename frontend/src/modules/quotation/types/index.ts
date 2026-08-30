export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CONVERTED';

/** Shared with the invoice module - mirrors LineDiscount.Type on the backend. */
export type { LineDiscountType } from '@/modules/invoice/types';
import type { LineDiscountType } from '@/modules/invoice/types';

export interface QuotationItemRequest {
  productId: number;
  quantity: number;
  discountType?: LineDiscountType;
  discountPercent?: number | null;
  discountAmountPaise?: number | null;
}

export interface QuotationRequest {
  customerName: string;
  customerMobile: string;
  customerEmail?: string | null;
  customerGstNo?: string | null;
  customerStateCode?: string | null;
  validUntil: string;
  items: QuotationItemRequest[];
  remarks?: string | null;
}

export interface QuotationItemResponse {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPriceDisplay: string;
  gstRatePercent: string;
  lineSubtotalDisplay: string;
  lineGstDisplay: string;
  lineTotalDisplay: string;
  /** CR-047 per-line discount. Mirrors the backend DTO exactly. */
  discountType: LineDiscountType;
  discountPercent: string;
  discountDisplay: string;
  /** Before discount; lineSubtotalDisplay is already net of it. */
  lineGrossDisplay: string;
}

export interface QuotationResponse {
  id: number;
  quotationNumber: string;
  customerId: number;
  customerName: string;
  customerMobile: string;
  quotationDate: string;
  validUntil: string;
  expired: boolean;
  /**
     * CR-049 discount ladder. The three discount fields are null when zero,
     * so an undiscounted quotation renders no discount rows at all.
     */
  grossSubtotalDisplay: string;
  productDiscountDisplay?: string | null;
  afterProductDiscountDisplay: string;
  quotationDiscountType: LineDiscountType;
  quotationDiscountPercent: string;
  quotationDiscountDisplay?: string | null;
  totalSavingsDisplay?: string | null;
  /** TAXABLE amount - net of both discounts. */
  subtotalDisplay: string;
  gstAmountDisplay: string;
  totalDisplay: string;
  status: QuotationStatus;
  remarks?: string | null;
  convertedInvoiceId?: number | null;
  items: QuotationItemResponse[];
  createdAt: string;
}

export interface QuotationSummaryResponse {
  id: number;
  quotationNumber: string;
  customerName: string;
  customerMobile: string;
  quotationDate: string;
  validUntil: string;
  expired: boolean;
  totalDisplay: string;
  status: QuotationStatus;
}

export interface QuotationSearchParams {
  search?: string;
  status?: QuotationStatus;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}
