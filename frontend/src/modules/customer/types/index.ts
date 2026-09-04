export type CustomerStatus = 'ACTIVE' | 'INACTIVE';

export interface CustomerRequest {
  customerName: string;
  mobileNo: string;
  email?: string | null;
  gstNo?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateCode?: string | null;
  pincode?: string | null;
  creditLimitPaise?: number | null;
  status: CustomerStatus;
  /** CR-056 §16 - null is treated as true (opted in) by the backend. */
  whatsappOptIn?: boolean | null;
}

export interface CustomerResponse {
  id: number;
  customerCode: string;
  customerName: string;
  mobileNo: string;
  email?: string | null;
  gstNo?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateCode?: string | null;
  pincode?: string | null;
  creditLimitDisplay: string;
  status: CustomerStatus;
  whatsappOptIn: boolean;
  createdAt: string;
}

export interface CustomerSummaryResponse {
  id: number;
  customerCode: string;
  customerName: string;
  mobileNo: string;
  city?: string | null;
  status: CustomerStatus;
}

export interface CustomerFinancialSummaryResponse {
  invoiceCount: number;
  quotationCount: number;
  totalInvoicedDisplay: string;
  totalPaidDisplay: string;
  outstandingBalanceDisplay: string;
}

export interface CustomerSearchParams {
  search?: string;
  status?: CustomerStatus;
  page?: number;
  size?: number;
}

/** Customer 360 (CR-030) - what this customer has bought before. */
export interface CustomerProductHistoryResponse {
  productId: number;
  productName: string;
  productCode: string;
  unit: string;
  totalQuantityPurchased: number;
  lastPriceDisplay: string;
  lastPurchaseDate: string;
}

/** CR-030 - credit-limit check by exact mobile number while building a new invoice. */
export interface CustomerCreditCheckResponse {
  customerId: number;
  customerName: string;
  creditLimitPaise: number;
  outstandingBalancePaise: number;
}
