export type InvoiceStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export type PaymentMethod = 'CASH' | 'UPI' | 'CARD' | 'BANK_TRANSFER' | 'OTHER';

/**
 * CR-047. Mirrors com.hardware.erp.common.util.LineDiscount.Type exactly -
 * the backend is the authority for every figure derived from it.
 */
/**
 * CR-050 retired the fixed-amount discount. Mirrors LineDiscount.Type on the
 * backend, where the constant no longer exists - keeping 'AMOUNT' here would
 * let the UI offer an option the API now rejects.
 */
export type LineDiscountType = 'NONE' | 'PERCENTAGE';

export interface InvoiceItemRequest {
  productId: number;
  quantity: number;
  /** Omitted entirely by older callers; the backend reads a missing type as NONE. */
  discountType?: LineDiscountType;
  /** Only sent for PERCENTAGE. 0-100. */
  discountPercent?: number | null;
  /** Only sent for AMOUNT. Paise, never rupees - matches the backend's money rule. */
  discountAmountPaise?: number | null;
  /** CR-053 backlog item 1. Bonus units given free - never priced, but deducted from stock alongside quantity. */
  freeQuantity?: number | null;
}

export interface InvoiceRequest {
  customerName: string;
  customerMobile: string;
  customerEmail?: string | null;
  customerGstNo?: string | null;
  customerStateCode?: string | null;
  items: InvoiceItemRequest[];
  initialPaymentPaise?: number | null;
  paymentMethod?: PaymentMethod | null;
  couponCode?: string | null;
  remarks?: string | null;
  transportMode?: string | null;
  vehicleNumber?: string | null;
  deliveryAddress?: string | null;
  bankAccountId?: number | null;
  bankAccountQrId?: number | null;
}

export interface InvoiceItemResponse {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unit: string;
  unitPriceDisplay: string;
  gstRatePercent: string;
  lineSubtotalDisplay: string;
  lineGstDisplay: string;
  lineTotalDisplay: string;
  discountType: LineDiscountType;
  discountPercent: string;
  discountDisplay: string;
  /** Before discount. lineSubtotalDisplay is already net of it. */
  lineGrossDisplay: string;
  /** CR-053 backlog item 1. Zero when the shop does not use free quantities. */
  freeQuantity: number;
}

export interface PaymentResponse {
  id: number;
  amountDisplay: string;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  notes?: string | null;
}

export interface InvoiceResponse {
  id: number;
  invoiceNumber: string;
  customerId: number;
  customerName: string;
  customerMobile: string;
  invoiceDate: string;
  subtotalDisplay: string;
  gstAmountDisplay: string;
  totalDisplay: string;
  couponCode?: string | null;
  discountDisplay?: string | null;
  paidDisplay: string;
  balanceDisplay: string;
  status: InvoiceStatus;
  remarks?: string | null;
  transportMode?: string | null;
  vehicleNumber?: string | null;
  deliveryAddress?: string | null;
  items: InvoiceItemResponse[];
  payments: PaymentResponse[];
  createdAt: string;
  bankAccountId?: number | null;
  bankAccountLabel?: string | null;
  bankAccountQrId?: number | null;
}

export interface InvoiceSummaryResponse {
  id: number;
  invoiceNumber: string;
  customerName: string;
  customerMobile: string;
  invoiceDate: string;
  totalDisplay: string;
  balanceDisplay: string;
  status: InvoiceStatus;
}

export interface InvoiceSearchParams {
  search?: string;
  status?: InvoiceStatus;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export interface PaymentRequest {
  amountPaise: number;
  paymentMethod: PaymentMethod;
  notes?: string | null;
}
