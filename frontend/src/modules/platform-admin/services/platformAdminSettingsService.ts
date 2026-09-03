import { platformAdminGet, platformAdminPut } from '@/services/platformAdminApiClient';
import type { RazorpayConfigResponse, UpdateRazorpayConfigRequest } from '../types';

/** Backend: platformadmin/controller/PlatformAdminRazorpaySettingsController.java (CR-057 phase 12) */
export const platformAdminSettingsService = {
  getRazorpayConfig: () => platformAdminGet<RazorpayConfigResponse>('/v1/platform-admin/settings/razorpay'),

  updateRazorpayConfig: (body: UpdateRazorpayConfigRequest) =>
    platformAdminPut<RazorpayConfigResponse>('/v1/platform-admin/settings/razorpay', body),
};
