export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CONVERTED';

export interface QuotationItemRequest {
  productId: number;
  quantity: number;
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
