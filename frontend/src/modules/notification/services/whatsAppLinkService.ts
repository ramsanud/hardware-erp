import { apiGet } from '@/services/apiClient';

/** Mirrors backend notification/dto/WhatsAppLinkResponse.java (CR-080). */
export interface WhatsAppLinkResponse {
  /** Absolute https://wa.me/... URL, already percent-encoded. Open it; never rebuild it. */
  url: string;
  /** The number as the shop entered it - for display, not for dialling. */
  toMobileNo: string;
  /** The plain text that will appear pre-filled in the chat. */
  message: string;
}

/**
 * CR-080 - manual WhatsApp. Every call is a GET with no side effect: the
 * server builds a wa.me link, the browser opens it, the person presses Send.
 * Nothing is sent or stored by the application, and no message is ever
 * posted through these calls - there is deliberately no method here that could.
 */
export const whatsAppLinkService = {
  invoice: (invoiceId: number) =>
    apiGet<WhatsAppLinkResponse>(`/v1/whatsapp/links/invoices/${invoiceId}`),

  paymentReminder: (invoiceId: number) =>
    apiGet<WhatsAppLinkResponse>(`/v1/whatsapp/links/invoices/${invoiceId}/reminder`),

  paymentReceipt: (invoiceId: number, paymentId: number) =>
    apiGet<WhatsAppLinkResponse>(`/v1/whatsapp/links/invoices/${invoiceId}/payments/${paymentId}`),

  quotation: (quotationId: number) =>
    apiGet<WhatsAppLinkResponse>(`/v1/whatsapp/links/quotations/${quotationId}`),

  customer: (customerId: number) =>
    apiGet<WhatsAppLinkResponse>(`/v1/whatsapp/links/customers/${customerId}`),
};
