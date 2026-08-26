import {
  apiDelete, apiGet, apiPatch, apiPost, apiPut,
} from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  ProjectExpenseRequest, ProjectExpenseResponse, ProjectMaterialRequest, ProjectMaterialResponse,
  ProjectPaymentRequest, ProjectPaymentResponse, ProjectRequest, ProjectResponse,
  ProjectSearchParams, ProjectStatusChangeRequest, ProjectSummaryResponse,
  RooftopCalculatorRequest, RooftopCalculatorResponse,
} from '../types';

/** Backend: project/controller/ProjectController.java, MaterialCalculatorController.java */
export const projectService = {
  search: (params: ProjectSearchParams) =>
    apiGet<PageResponse<ProjectSummaryResponse>>('/v1/projects', { params }),

  get: (id: number) => apiGet<ProjectResponse>(`/v1/projects/${id}`),

  create: (body: ProjectRequest) => apiPost<ProjectResponse>('/v1/projects', body),

  update: (id: number, body: ProjectRequest) => apiPut<ProjectResponse>(`/v1/projects/${id}`, body),

  changeStatus: (id: number, body: ProjectStatusChangeRequest) =>
    apiPatch<ProjectResponse>(`/v1/projects/${id}/status`, body),

  materials: (id: number) => apiGet<ProjectMaterialResponse[]>(`/v1/projects/${id}/materials`),
  addMaterial: (id: number, body: ProjectMaterialRequest) =>
    apiPost<ProjectMaterialResponse>(`/v1/projects/${id}/materials`, body),
  updateMaterial: (id: number, materialId: number, body: ProjectMaterialRequest) =>
    apiPut<ProjectMaterialResponse>(`/v1/projects/${id}/materials/${materialId}`, body),
  removeMaterial: (id: number, materialId: number) =>
    apiDelete(`/v1/projects/${id}/materials/${materialId}`),

  expenses: (id: number) => apiGet<ProjectExpenseResponse[]>(`/v1/projects/${id}/expenses`),
  addExpense: (id: number, body: ProjectExpenseRequest) =>
    apiPost<ProjectExpenseResponse>(`/v1/projects/${id}/expenses`, body),
  removeExpense: (id: number, expenseId: number) =>
    apiDelete(`/v1/projects/${id}/expenses/${expenseId}`),

  payments: (id: number) => apiGet<ProjectPaymentResponse[]>(`/v1/projects/${id}/payments`),
  addPayment: (id: number, body: ProjectPaymentRequest) =>
    apiPost<ProjectPaymentResponse>(`/v1/projects/${id}/payments`, body),

  calculateRooftopSheets: (body: RooftopCalculatorRequest) =>
    apiPost<RooftopCalculatorResponse>('/v1/projects/calculators/rooftop-sheet', body),
};
