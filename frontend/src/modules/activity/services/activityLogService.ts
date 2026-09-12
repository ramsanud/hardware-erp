import { apiGet } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type { ActivityLogResponse, ActivityLogSearchParams } from '../types';

/**
 * Backend: common/activity/ActivityLogController.java (CR-072).
 *
 * Read only, and there is no write or delete counterpart on purpose - an
 * audit trail an operator can prune is not an audit trail.
 */
export const activityLogService = {
  search: (params: ActivityLogSearchParams) =>
    apiGet<PageResponse<ActivityLogResponse>>('/v1/activity-log', { params }),

  /** The module codes this shop actually has history for. */
  moduleCodes: () => apiGet<string[]>('/v1/activity-log/modules'),
};
