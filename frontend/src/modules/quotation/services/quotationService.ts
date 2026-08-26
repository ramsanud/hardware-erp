import {
  apiGet, apiGetBlob, apiPatch, apiPost, apiPut,
} from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  QuotationRequest, QuotationResponse, QuotationSearchParams, QuotationStatus, QuotationSummaryResponse,
} from '../types';

/** Backend: quotation/controller/QuotationController.java */
export const quotationService = {
  search: (params: QuotationSearchParams) =>
    apiGet<PageResponse<QuotationSummaryResponse>>('/v1/quotations', { params }),

  get: (id: number) => apiGet<QuotationResponse>(`/v1/quotations/${id}`),

  create: (body: QuotationRequest) => apiPost<QuotationResponse>('/v1/quotations', body),

  /** Edit a DRAFT or SENT quotation in place, keeping its number. */
  update: (id: number, body: QuotationRequest) => apiPut<QuotationResponse>(`/v1/quotations/${id}`, body),

  updateStatus: (id: number, status: QuotationStatus) =>
    apiPatch<QuotationResponse>(`/v1/quotations/${id}/status`, { status }),

  convert: (id: number) => apiPost<QuotationResponse>(`/v1/quotations/${id}/convert`),

  pdf: (id: number) => apiGetBlob(`/v1/quotations/${id}/pdf`),
};
