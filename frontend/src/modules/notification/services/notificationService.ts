import { apiGet, apiPost } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';

export interface ContactAdminRequest {
  subject: string;
  message: string;
}

export type NotificationChannel = 'EMAIL' | 'SMS' | 'WHATSAPP';
export type NotificationLogStatus = 'SENT' | 'LOGGED_ONLY' | 'FAILED' | 'DELIVERED' | 'READ';

/** Mirrors backend notification/dto/NotificationLogResponse.java. */
export interface NotificationLogResponse {
  id: number;
  channel: NotificationChannel;
  recipient: string;
  subject?: string | null;
  body: string;
  status: NotificationLogStatus;
  relatedEntityType?: string | null;
  relatedEntityId?: number | null;
  providerMessageId?: string | null;
  createdAt: string;
}

/** Backend: notification/controller/NotificationController.java */
export const notificationService = {
  contactAdmin: (body: ContactAdminRequest) => apiPost<void>('/v1/notifications/contact-admin', body),

  /** CR-056 §13 - Message History. channel omitted returns every channel. */
  log: (params: { channel?: NotificationChannel; page?: number; size?: number }) =>
    apiGet<PageResponse<NotificationLogResponse>>('/v1/notifications/log', { params }),
};
