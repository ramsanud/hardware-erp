import { platformAdminGet } from '@/services/platformAdminApiClient';
import type { PageResponse } from '@/shared/types/api';
import type { PlatformAuditLogResponse } from '../types';

export interface AuditLogSearchParams {
  adminId?: number;
  action?: string;
  success?: boolean;
  targetType?: string;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const platformAdminAuditLogService = {
  search(params: AuditLogSearchParams) {
    return platformAdminGet<PageResponse<PlatformAuditLogResponse>>('/v1/platform-admin/audit-logs', { params });
  },
};
