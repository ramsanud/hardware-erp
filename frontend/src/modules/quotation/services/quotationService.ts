import {
  apiDelete, apiGet, apiGetBlob, apiPatch, apiPost, apiPut,
} from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  QuotationRequest, QuotationResponse, QuotationSearchParams, QuotationStatsParams, QuotationStatsResponse,
  QuotationStatus, QuotationSummaryResponse,
} from '../types';

/** Backend: quotation/controller/QuotationController.java */
export const quotationService = {
  search: (params: QuotationSearchParams) =>
    apiGet<PageResponse<QuotationSummaryResponse>>('/v1/quotations', { params }),

  /** CR-083. KPI cards - same search and date range as the list, every status. */
  stats: (params: QuotationStatsParams) =>
    apiGet<QuotationStatsResponse>('/v1/quotations/stats', { params }),

  get: (id: number) => apiGet<QuotationResponse>(`/v1/quotations/${id}`),

  create: (body: QuotationRequest) => apiPost<QuotationResponse>('/v1/quotations', body),

  /** Edit a DRAFT or SENT quotation in place, keeping its number. */
  update: (id: number, body: QuotationRequest) => apiPut<QuotationResponse>(`/v1/quotations/${id}`, body),

  updateStatus: (id: number, status: QuotationStatus) =>
    apiPatch<QuotationResponse>(`/v1/quotations/${id}/status`, { status }),

  /** CR-083. DRAFT only; the server refuses anything later and says to reject it instead. */
  delete: (id: number) => apiDelete(`/v1/quotations/${id}`),

  convert: (id: number) => apiPost<QuotationResponse>(`/v1/quotations/${id}/convert`),

  pdf: (id: number) => apiGetBlob(`/v1/quotations/${id}/pdf`),
};
