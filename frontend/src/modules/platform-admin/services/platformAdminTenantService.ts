import { platformAdminGet, platformAdminPost } from '@/services/platformAdminApiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  PlatformDashboardResponse,
  PlatformTenantDetailResponse,
  PlatformTenantSummaryResponse,
  SubscriptionTier,
  TenantStatus,
} from '../types';

export interface TenantListParams {
  search?: string;
  status?: TenantStatus;
  tier?: SubscriptionTier;
  page?: number;
  size?: number;
}

export const platformAdminDashboardService = {
  overview() {
    return platformAdminGet<PlatformDashboardResponse>('/v1/platform-admin/dashboard');
  },
};

export const platformAdminTenantService = {
  list(params: TenantListParams) {
    return platformAdminGet<PageResponse<PlatformTenantSummaryResponse>>('/v1/platform-admin/tenants', { params });
  },

  get(id: number) {
    return platformAdminGet<PlatformTenantDetailResponse>(`/v1/platform-admin/tenants/${id}`);
  },

  suspend(id: number, reason: string) {
    return platformAdminPost<PlatformTenantSummaryResponse>(
      `/v1/platform-admin/tenants/${id}/suspend`, { reason });
  },

  reactivate(id: number) {
    return platformAdminPost<PlatformTenantSummaryResponse>(`/v1/platform-admin/tenants/${id}/reactivate`);
  },
};
