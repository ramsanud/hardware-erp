import { apiGet, apiPost } from '@/services/apiClient';
import type { TenantRegistrationRequest, TenantRegistrationResponse } from '../types';

/** Backend: tenant/controller/TenantRegistrationController.java - public, unauthenticated, rate-limited. */
export const tenantRegistrationService = {
  register: (body: TenantRegistrationRequest) =>
    apiPost<TenantRegistrationResponse>('/v1/tenants/register', body),

  slugAvailable: (slug: string) =>
    apiGet<{ available: boolean }>('/v1/tenants/register/slug-available', { params: { slug } }),

  /**
   * CR-062. Asked on the wizard's Next, so a mobile number or email that is
   * already registered is rejected at the step that collects it rather than
   * after the plan has been picked and the Terms accepted.
   */
  identifierAvailable: (params: { mobileNo?: string; email?: string }) =>
    apiGet<{ mobileAvailable: boolean; emailAvailable: boolean }>(
      '/v1/tenants/register/identifier-available', { params },
    ),
};
