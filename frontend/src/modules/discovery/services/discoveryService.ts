import { apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  DiscoverySettingRequest, DiscoverySettingResponse, NearbyAvailabilityResponse, OwnerNotificationResponse,
} from '../types';

/** Backend: discovery/controller/*.java (CR-090) */
export const discoveryService = {
  settings: () => apiGet<DiscoverySettingResponse>('/v1/discovery/settings'),

  updateSettings: (body: DiscoverySettingRequest) =>
    apiPut<DiscoverySettingResponse>('/v1/discovery/settings', body),

  discover: (requestId: number) =>
    apiPost<NearbyAvailabilityResponse>(`/v1/product-requests/${requestId}/discover`),

  nearby: (requestId: number) =>
    apiGet<NearbyAvailabilityResponse>(`/v1/product-requests/${requestId}/nearby`),

  notifications: (unreadOnly: boolean, page = 0, size = 20) =>
    apiGet<PageResponse<OwnerNotificationResponse>>('/v1/owner-notifications', { params: { unreadOnly, page, size } }),

  unreadCount: () => apiGet<{ unread: number }>('/v1/owner-notifications/unread-count'),

  markRead: (id: number) => apiPost<OwnerNotificationResponse>(`/v1/owner-notifications/${id}/read`),

  markAllRead: () => apiPost<{ marked: number }>('/v1/owner-notifications/read-all'),
};
