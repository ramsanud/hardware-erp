import { apiGet, apiPost } from '@/services/apiClient';
import type { TenantRegistrationRequest, TenantRegistrationResponse } from '../types';

/** Backend: tenant/controller/TenantRegistrationController.java - public, unauthenticated, rate-limited. */
export const tenantRegistrationService = {
  register: (body: TenantRegistrationRequest) =>
    apiPost<TenantRegistrationResponse>('/v1/tenants/register', body),

  slugAvailable: (slug: string) =>
    apiGet<{ available: boolean }>('/v1/tenants/register/slug-available', { params: { slug } }),
};
