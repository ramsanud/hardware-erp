import { platformAdminGet, platformAdminPost } from '@/services/platformAdminApiClient';
import type { PlatformAdminActiveSessionResponse, PlatformSecurityDashboardResponse } from '../types';

export const platformAdminSecurityService = {
  dashboard() {
    return platformAdminGet<PlatformSecurityDashboardResponse>('/v1/platform-admin/security/dashboard');
  },
  mySessions() {
    return platformAdminGet<PlatformAdminActiveSessionResponse[]>('/v1/platform-admin/security/sessions');
  },
  revokeSession(id: number) {
    return platformAdminPost<void>(`/v1/platform-admin/security/sessions/${id}/revoke`);
  },
  revokeOtherSessions() {
    return platformAdminPost<number>('/v1/platform-admin/security/sessions/revoke-others');
  },
};
