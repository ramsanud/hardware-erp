import type { PaymentMethod } from '@/modules/invoice/types';

export type { PaymentMethod };

/** Mirrors backend PaymentSummaryResponse (invoice/dto/PaymentSummaryResponse.java). */
export interface PaymentSummaryResponse {
  id: number;
  invoiceId: number;
  invoiceNumber: string;
  customerName: string;
  customerMobile: string;
  amountPaise: number;
  amountDisplay: string;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  notes?: string | null;
}

export interface PaymentSearchParams {
  search?: string;
  paymentMethod?: PaymentMethod;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}
