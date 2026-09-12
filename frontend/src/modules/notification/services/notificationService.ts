import { apiGet, apiPost, apiPostForm } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';

export interface ContactAdminRequest {
  subject: string;
  message: string;
  /** CR-073. Optional screenshot; emailed to support, never stored. */
  screenshot?: File | null;
}

/** Mirrors backend common/image/ImageValidation.java - the same 2MB cap and type set. */
export const SCREENSHOT_MAX_BYTES = 2 * 1024 * 1024;
export const SCREENSHOT_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

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
  /**
   * Stays on the JSON endpoint when nothing is attached (CR-073). Both paths
   * exist server-side; sending a multipart body for a text-only report would
   * be heavier for no gain, and the JSON contract keeps its own Postman entry.
   */
  contactAdmin: ({ subject, message, screenshot }: ContactAdminRequest) => (
    screenshot
      ? apiPostForm<void>('/v1/notifications/contact-admin', { subject, message, screenshot })
      : apiPost<void>('/v1/notifications/contact-admin', { subject, message })
  ),

  /** CR-056 §13 - Message History. channel omitted returns every channel. */
  log: (params: { channel?: NotificationChannel; page?: number; size?: number }) =>
    apiGet<PageResponse<NotificationLogResponse>>('/v1/notifications/log', { params }),
};
