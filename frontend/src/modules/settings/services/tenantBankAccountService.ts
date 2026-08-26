import {
  apiDelete, apiGet, apiPost, apiPut, http,
} from '@/services/apiClient';
import type { ApiResponse } from '@/shared/types/api';
import type { TenantBankAccountRequest, TenantBankAccountResponse } from '../types';

/** Backend: tenant/controller/TenantBankAccountController.java (CR-036) */
export const tenantBankAccountService = {
  list: () => apiGet<TenantBankAccountResponse[]>('/v1/settings/bank-accounts'),

  create: (body: TenantBankAccountRequest) =>
    apiPost<TenantBankAccountResponse>('/v1/settings/bank-accounts', body),

  update: (id: number, body: TenantBankAccountRequest) =>
    apiPut<TenantBankAccountResponse>(`/v1/settings/bank-accounts/${id}`, body),

  remove: (id: number) => apiDelete(`/v1/settings/bank-accounts/${id}`),

  reveal: (id: number) => apiGet<string>(`/v1/settings/bank-accounts/${id}/reveal`),

  addQr: async (bankAccountId: number, label: string, file: File): Promise<TenantBankAccountResponse> => {
    const form = new FormData();
    form.append('label', label);
    form.append('file', file);
    const { data } = await http.post<ApiResponse<TenantBankAccountResponse>>(
      `/v1/settings/bank-accounts/${bankAccountId}/qr`, form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data.data;
  },

  removeQr: (qrId: number) => apiDelete(`/v1/settings/bank-accounts/qr/${qrId}`),

  qrImageUrl: (qrId: number) => `/v1/settings/bank-accounts/qr/${qrId}/image`,
};
