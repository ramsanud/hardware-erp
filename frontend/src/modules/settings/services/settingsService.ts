import {
  apiDelete, apiGet, apiPut, apiUploadFile,
} from '@/services/apiClient';
import type { TenantSettingsRequest, TenantSettingsResponse, UsageSummaryResponse } from '../types';

/** Backend: tenant/controller/TenantSettingsController.java + TenantImageController.java */
export const settingsService = {
  get: () => apiGet<TenantSettingsResponse>('/v1/settings'),

  update: (body: TenantSettingsRequest) => apiPut<TenantSettingsResponse>('/v1/settings', body),

  /** CR-031 - usage against the tenant's tier entitlement limits. */
  usage: () => apiGet<UsageSummaryResponse>('/v1/settings/usage'),

  logoUrl: '/v1/settings/logo',
  uploadLogo: (file: File) => apiUploadFile('/v1/settings/logo', file),
  removeLogo: () => apiDelete('/v1/settings/logo'),

  signatureUrl: '/v1/settings/signature',
  uploadSignature: (file: File) => apiUploadFile('/v1/settings/signature', file),
  removeSignature: () => apiDelete('/v1/settings/signature'),

  upiQrUrl: '/v1/settings/upi-qr',
  uploadUpiQr: (file: File) => apiUploadFile('/v1/settings/upi-qr', file),
  removeUpiQr: () => apiDelete('/v1/settings/upi-qr'),
};
