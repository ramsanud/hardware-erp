import { apiGet } from '@/services/apiClient';
import type { PermissionGroupResponse, PermissionResponse } from '../types';

/** Backend: auth/controller/PermissionController.java */
export const permissionService = {
  list: () => apiGet<PermissionResponse[]>('/v1/permissions'),

  grouped: () => apiGet<PermissionGroupResponse[]>('/v1/permissions/grouped'),
};
