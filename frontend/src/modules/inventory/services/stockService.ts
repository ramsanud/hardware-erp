import { apiGet, apiPost } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type { StockAdjustmentRequest, StockResponse, StockSearchParams } from '../types';

/** Backend: inventory/controller/StockController.java */
export const stockService = {
  search: (params: StockSearchParams) =>
    apiGet<PageResponse<StockResponse>>('/v1/stock', { params }),

  adjust: (productId: number, body: StockAdjustmentRequest) =>
    apiPost(`/v1/stock/${productId}/adjust`, body),
};
