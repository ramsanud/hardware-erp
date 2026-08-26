import { apiGet, apiPost } from '@/services/apiClient';
import type { WorkerPaymentRequest, WorkerPaymentResponse, WorkerWageSummaryResponse } from '../types';

/** Backend: labour/controller/WorkerPaymentController.java */
export const workerPaymentService = {
  create: (body: WorkerPaymentRequest) => apiPost<WorkerPaymentResponse>('/v1/worker-payments', body),

  listForWorker: (workerId: number) =>
    apiGet<WorkerPaymentResponse[]>(`/v1/workers/${workerId}/payments`),

  /** Soft cancel - the payment stays in history but stops counting towards the paid total. */
  cancel: (id: number) => apiPost<void>(`/v1/worker-payments/${id}/cancel`),

  wageSummary: (workerId: number, fromDate?: string, toDate?: string) =>
    apiGet<WorkerWageSummaryResponse>(`/v1/workers/${workerId}/wage-summary`, { params: { fromDate, toDate } }),
};
