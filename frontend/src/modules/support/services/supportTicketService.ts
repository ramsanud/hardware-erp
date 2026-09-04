import { apiGet, apiPost } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  CreateTicketRequest, SupportTicketDetailResponse, SupportTicketSummaryResponse, TicketMessageRequest,
} from '../types';

export const supportTicketService = {
  create: (body: CreateTicketRequest) => apiPost<SupportTicketSummaryResponse>('/v1/support-tickets', body),

  list: (page: number, size: number) =>
    apiGet<PageResponse<SupportTicketSummaryResponse>>('/v1/support-tickets', { params: { page, size } }),

  get: (id: number) => apiGet<SupportTicketDetailResponse>(`/v1/support-tickets/${id}`),

  reply: (id: number, body: TicketMessageRequest) =>
    apiPost<SupportTicketDetailResponse>(`/v1/support-tickets/${id}/messages`, body),
};
