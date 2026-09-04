import { platformAdminGet } from '@/services/platformAdminApiClient';
import type { PlatformBillingOverviewResponse, TenantBillingHistoryResponse } from '../types';

/** Backend: platformadmin/controller/PlatformAdminBillingController.java (CR-057 phase 9) */
export const platformAdminBillingService = {
  overview: () => platformAdminGet<PlatformBillingOverviewResponse>('/v1/platform-admin/billing/overview'),

  tenantHistory: (tenantId: number) =>
    platformAdminGet<TenantBillingHistoryResponse>(`/v1/platform-admin/billing/tenants/${tenantId}`),
};
