import { apiGet, apiPost, http } from '@/services/apiClient';
import type { PageResponse, ApiResponse } from '@/shared/types/api';
import type {
  ImportConfirmRequest, ImportPreviewResponse, ImportResultResponse,
  PurchaseRequest, PurchaseResponse, PurchaseSummaryResponse, PurchaseStatus,
  RecordPurchasePaymentRequest,
} from '../types';

export interface PurchaseSearchParams {
  search?: string;
  status?: PurchaseStatus;
  page?: number;
  size?: number;
}

/** Backend: purchase/controller/PurchaseController.java, PurchaseImportController.java */
export const purchaseService = {
  search: (params: PurchaseSearchParams) =>
    apiGet<PageResponse<PurchaseSummaryResponse>>('/v1/purchases', { params }),

  get: (id: number) => apiGet<PurchaseResponse>(`/v1/purchases/${id}`),

  create: (body: PurchaseRequest) => apiPost<PurchaseResponse>('/v1/purchases', body),

  addPayment: (id: number, body: RecordPurchasePaymentRequest) =>
    apiPost<PurchaseResponse>(`/v1/purchases/${id}/payments`, body),

  cancel: (id: number) => apiPost<PurchaseResponse>(`/v1/purchases/${id}/cancel`),

  documentUrl: (id: number) => `/v1/purchases/${id}/document`,

  /** Preview never writes anything server-side - purely parses the file and returns matched/new detection per row. */
  importPreview: async (file: File): Promise<ImportPreviewResponse> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await http.post<ApiResponse<ImportPreviewResponse>>(
      '/v1/purchases/import/preview', form, { headers: { 'Content-Type': 'multipart/form-data' } });
    return data.data;
  },

  /** The only import call that persists anything - one transaction, all or nothing. Re-sends the same file so the original bill is stored against the Purchase it produced. */
  importConfirm: async (file: File, request: ImportConfirmRequest): Promise<ImportResultResponse> => {
    const form = new FormData();
    form.append('file', file);
    form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    const { data } = await http.post<ApiResponse<ImportResultResponse>>(
      '/v1/purchases/import/confirm', form, { headers: { 'Content-Type': 'multipart/form-data' } });
    return data.data;
  },
};
