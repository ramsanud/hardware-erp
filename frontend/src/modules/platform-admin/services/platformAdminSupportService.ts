import { platformAdminGet, platformAdminPost } from '@/services/platformAdminApiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  PlatformSupportDashboardResponse, SupportTicketDetailResponse, SupportTicketSummaryResponse,
  TicketCategory, TicketPriority, TicketStatus,
} from '../types';

export interface TicketSearchParams {
  search?: string;
  status?: TicketStatus;
  priority?: TicketPriority;
  category?: TicketCategory;
  assignedAdminId?: number;
  page?: number;
  size?: number;
}

export const platformAdminSupportService = {
  dashboard() {
    return platformAdminGet<PlatformSupportDashboardResponse>('/v1/platform-admin/support/dashboard');
  },
  search(params: TicketSearchParams) {
    return platformAdminGet<PageResponse<SupportTicketSummaryResponse>>('/v1/platform-admin/support/tickets', { params });
  },
  get(id: number) {
    return platformAdminGet<SupportTicketDetailResponse>(`/v1/platform-admin/support/tickets/${id}`);
  },
  reply(id: number, message: string, internal: boolean) {
    return platformAdminPost<SupportTicketDetailResponse>(
      `/v1/platform-admin/support/tickets/${id}/messages`, { message, internal });
  },
  assign(id: number, assigneeAdminId: number) {
    return platformAdminPost<SupportTicketSummaryResponse>(
      `/v1/platform-admin/support/tickets/${id}/assign/${assigneeAdminId}`);
  },
  changePriority(id: number, priority: TicketPriority) {
    return platformAdminPost<SupportTicketSummaryResponse>(
      `/v1/platform-admin/support/tickets/${id}/priority/${priority}`);
  },
  changeStatus(id: number, status: TicketStatus) {
    return platformAdminPost<SupportTicketSummaryResponse>(
      `/v1/platform-admin/support/tickets/${id}/status/${status}`);
  },
};
