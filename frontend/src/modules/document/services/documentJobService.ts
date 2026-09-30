import { apiGet, apiGetBlob, apiPost } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type { WhatsAppLinkResponse } from '@/modules/notification/services/whatsAppLinkService';
import type { ReportJob, ReportJobRequest } from '../types';

/**
 * Backend: document/controller/ReportJobController.java and
 * ShareDispatcherController.java (CR-101). The async counterpart to
 * reportService.export: the same reports, built off the request thread,
 * plus PNG and GSTR-1's JSON.
 */
export const documentJobService = {
  enqueue: (request: ReportJobRequest) => apiPost<ReportJob>('/v1/documents/jobs', request),
  status: (id: number) => apiGet<ReportJob>(`/v1/documents/jobs/${id}`),
  list: (page = 0, size = 20) =>
    apiGet<PageResponse<ReportJob>>('/v1/documents/jobs', { params: { page, size } }),
  download: (id: number) => apiGetBlob(`/v1/documents/jobs/${id}/download`),

  /** wa.me link with the file's caption; no number opens WhatsApp's own contact chooser. */
  whatsAppLink: (id: number, toMobileNo?: string) =>
    apiGet<WhatsAppLinkResponse>(`/v1/documents/jobs/${id}/share/whatsapp-link`,
      { params: toMobileNo ? { toMobileNo } : {} }),
  email: (id: number, toEmail: string) =>
    apiPost<'SENT' | 'LOGGED_ONLY' | 'FAILED'>(`/v1/documents/jobs/${id}/share/email`, { toEmail }),

  /** CR-101's first Smart Greetings entry - festival / birthday / anniversary wording, opened manually. */
  greetingLink: (customerId: number, occasion: string) =>
    apiGet<WhatsAppLinkResponse>(`/v1/customers/${customerId}/greeting-link`, { params: { occasion } }),
};
