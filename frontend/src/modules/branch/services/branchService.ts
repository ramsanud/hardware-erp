import { apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  BranchRequest, BranchResponse, BranchStockResponse, BranchSummaryResponse,
  StockTransferRequest, StockTransferResponse,
} from '../types';

/** Backend: branch/controller/BranchController.java (CR-092) */
export const branchService = {
  list: () => apiGet<BranchResponse[]>('/v1/branches'),
  create: (body: BranchRequest) => apiPost<BranchResponse>('/v1/branches', body),
  update: (id: number, body: BranchRequest) => apiPut<BranchResponse>(`/v1/branches/${id}`, body),
  summary: (from: string, to: string) =>
    apiGet<BranchSummaryResponse[]>('/v1/branches/summary', { params: { from, to } }),
  stock: (branchId?: number | null, search?: string) =>
    apiGet<BranchStockResponse[]>('/v1/branches/stock', { params: { branchId: branchId ?? undefined, search: search || undefined } }),
  transfers: (page = 0, size = 20) =>
    apiGet<PageResponse<StockTransferResponse>>('/v1/branches/transfers', { params: { page, size } }),
  transfer: (body: StockTransferRequest) => apiPost<StockTransferResponse>('/v1/branches/transfers', body),
  assignUser: (userId: number, branchId: number | null) =>
    apiPut<BranchResponse | null>(`/v1/branches/users/${userId}`, { branchId }),
};
