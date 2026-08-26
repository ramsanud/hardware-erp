export type InvoiceStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export type PaymentMethod = 'CASH' | 'UPI' | 'CARD' | 'BANK_TRANSFER' | 'OTHER';

export interface InvoiceItemRequest {
  productId: number;
  quantity: number;
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
