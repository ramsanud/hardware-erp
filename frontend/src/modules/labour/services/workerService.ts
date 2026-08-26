import { apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type { WorkerRequest, WorkerResponse, WorkerSearchParams } from '../types';

/** Backend: labour/controller/WorkerController.java */
export const workerService = {
  search: (params: WorkerSearchParams) =>
    apiGet<PageResponse<WorkerResponse>>('/v1/workers', { params }),

  listActive: () => apiGet<WorkerResponse[]>('/v1/workers/active'),

  get: (id: number) => apiGet<WorkerResponse>(`/v1/workers/${id}`),

  create: (body: WorkerRequest) => apiPost<WorkerResponse>('/v1/workers', body),

  update: (id: number, body: WorkerRequest) => apiPut<WorkerResponse>(`/v1/workers/${id}`, body),

  deactivate: (id: number) => apiPost<void>(`/v1/workers/${id}/deactivate`),
  activate: (id: number) => apiPost<void>(`/v1/workers/${id}/activate`),
};
