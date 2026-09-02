import { apiGet, apiPost } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type { StockAdjustmentRequest, StockResponse, StockSearchParams } from '../types';

/** Backend: inventory/controller/StockController.java */
export const stockService = {
  search: (params: StockSearchParams) =>
    apiGet<PageResponse<StockResponse>>('/v1/stock', { params }),

  adjust: (productId: number, body: StockAdjustmentRequest) =>
    apiPost(`/v1/stock/${productId}/adjust`, body),

  /** CR-056 §11 - backend: notification/reminder/LowStockAlertController.java. Returns how many products triggered it. */
  sendLowStockAlert: () => apiPost<number>('/v1/inventory/low-stock/send-alert'),
};
