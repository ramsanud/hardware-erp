import { apiGet, apiPost } from '@/services/apiClient';
import type { WhatsAppConnectionRequest, WhatsAppConnectionResponse } from '../types';

/** Backend: notification/controller/TenantWhatsAppConnectionController.java (CR-056) */
export const whatsAppConnectionService = {
  getStatus: () => apiGet<WhatsAppConnectionResponse>('/v1/settings/whatsapp'),

  connect: (body: WhatsAppConnectionRequest) =>
    apiPost<WhatsAppConnectionResponse>('/v1/settings/whatsapp/connect', body),

  disconnect: () => apiPost<WhatsAppConnectionResponse>('/v1/settings/whatsapp/disconnect'),

  testSend: (toMobileNo: string) =>
    apiPost<'SENT' | 'LOGGED_ONLY' | 'FAILED'>('/v1/settings/whatsapp/test-send', { toMobileNo }),
};
