import { platformAdminGet, platformAdminGetBlob } from '@/services/platformAdminApiClient';
import type { TenantAnalyticsResponse } from '../types';

/** Backend: platformadmin/controller/PlatformAdminAnalyticsController.java (CR-057 phase 10) */
export const platformAdminAnalyticsService = {
  overview: () => platformAdminGet<TenantAnalyticsResponse>('/v1/platform-admin/analytics/overview'),

  export: (format: 'csv' | 'xlsx' | 'pdf') =>
    platformAdminGetBlob('/v1/platform-admin/analytics/export', { params: { format } }),
};
