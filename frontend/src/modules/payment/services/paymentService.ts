import { apiGet } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type { PaymentSearchParams, PaymentSummaryResponse } from '../types';

/** Backend: invoice/controller/PaymentController.java */
export const paymentService = {
  search: (params: PaymentSearchParams) =>
    apiGet<PageResponse<PaymentSummaryResponse>>('/v1/payments', { params }),
};
