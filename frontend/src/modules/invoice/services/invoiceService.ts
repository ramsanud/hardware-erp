import { apiGet, apiGetBlob, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  InvoiceRequest, InvoiceResponse, InvoiceSearchParams, InvoiceSummaryResponse, PaymentRequest,
} from '../types';

/** Backend: invoice/controller/InvoiceController.java */
export const invoiceService = {
  search: (params: InvoiceSearchParams) =>
    apiGet<PageResponse<InvoiceSummaryResponse>>('/v1/invoices', { params }),

  get: (id: number) => apiGet<InvoiceResponse>(`/v1/invoices/${id}`),

  create: (body: InvoiceRequest) => apiPost<InvoiceResponse>('/v1/invoices', body),

  /** Amend an unpaid invoice in place. Refused by the server once any payment exists. */
  update: (id: number, body: InvoiceRequest) => apiPut<InvoiceResponse>(`/v1/invoices/${id}`, body),

  addPayment: (id: number, body: PaymentRequest) =>
    apiPost<InvoiceResponse>(`/v1/invoices/${id}/payments`, body),

  cancel: (id: number) => apiPost<InvoiceResponse>(`/v1/invoices/${id}/cancel`),

  pdf: (id: number) => apiGetBlob(`/v1/invoices/${id}/pdf`),

  emailInvoice: (id: number, toEmail: string) =>
    apiPost<'SENT' | 'LOGGED_ONLY' | 'FAILED'>(`/v1/invoices/${id}/share/email`, { toEmail }),

  /** Task 05 (WhatsApp reminders). Synchronous - the response is the real resulting status, never assumed. */
  sendPaymentReminder: (id: number) =>
    apiPost<'SENT' | 'LOGGED_ONLY' | 'FAILED'>(`/v1/invoices/${id}/remind`),

  /** CR-056 - manual resend of the invoice-created message over WhatsApp. */
  sendInvoiceViaWhatsApp: (id: number) =>
    apiPost<'SENT' | 'LOGGED_ONLY' | 'FAILED'>(`/v1/invoices/${id}/share/whatsapp`),

  /** CR-056 - manual only, one recorded payment. */
  sendPaymentReceiptViaWhatsApp: (invoiceId: number, paymentId: number) =>
    apiPost<'SENT' | 'LOGGED_ONLY' | 'FAILED'>(`/v1/invoices/${invoiceId}/payments/${paymentId}/share/whatsapp`),
};
