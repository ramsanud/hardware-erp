import { apiGet } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type { AuditSearchParams, SecurityAuditLogResponse } from '../types';

/** Backend: auth/controller/SecurityAuditLogController.java (added under CR-013) */
export const securityAuditService = {
  search: (params: AuditSearchParams) =>
    apiGet<PageResponse<SecurityAuditLogResponse>>('/v1/security-audit-logs', { params }),
};
