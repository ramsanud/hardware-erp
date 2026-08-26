import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { RoleRequest, RoleResponse } from '../types';

/** Backend: auth/controller/RoleController.java */
export const roleService = {
  list: () => apiGet<RoleResponse[]>('/v1/roles'),

  get: (id: number) => apiGet<RoleResponse>(`/v1/roles/${id}`),

  create: (body: RoleRequest) => apiPost<RoleResponse>('/v1/roles', body),

  update: (id: number, body: RoleRequest) => apiPut<RoleResponse>(`/v1/roles/${id}`, body),

  remove: (id: number) => apiDelete(`/v1/roles/${id}`),
};
