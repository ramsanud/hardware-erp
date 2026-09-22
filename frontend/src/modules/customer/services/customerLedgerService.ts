import { apiGet, apiPost } from '@/services/apiClient';
import type {
  AgeingResponse, LedgerAdjustmentRequest, LedgerBalanceResponse, StatementResponse,
} from '../types/ledger';

/** Backend: customer/ledger/CustomerLedgerController.java (CR-091 Phase 4) */
export const customerLedgerService = {
  balance: (customerId: number) =>
    apiGet<LedgerBalanceResponse>(`/v1/customers/${customerId}/ledger/balance`),

  statement: (customerId: number, from: string, to: string) =>
    apiGet<StatementResponse>(`/v1/customers/${customerId}/ledger/statement`, { params: { from, to } }),

  ageing: (customerId: number) =>
    apiGet<AgeingResponse>(`/v1/customers/${customerId}/ledger/ageing`),

  /** PAYMENT_MANAGE. A reason is mandatory - the server refuses a blank one. */
  adjust: (customerId: number, body: LedgerAdjustmentRequest) =>
    apiPost<LedgerBalanceResponse>(`/v1/customers/${customerId}/ledger/adjust`, body),
};
