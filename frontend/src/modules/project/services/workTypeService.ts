import { apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { WorkTypeRequest, WorkTypeResponse } from '../types';

/** Backend: project/controller/WorkTypeController.java */
export const workTypeService = {
  list: () => apiGet<WorkTypeResponse[]>('/v1/work-types'),

  create: (body: WorkTypeRequest) => apiPost<WorkTypeResponse>('/v1/work-types', body),

  update: (id: number, body: WorkTypeRequest) => apiPut<WorkTypeResponse>(`/v1/work-types/${id}`, body),
};
