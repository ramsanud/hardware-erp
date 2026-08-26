import { apiPost } from '@/services/apiClient';

export interface ContactAdminRequest {
  subject: string;
  message: string;
}

/** Backend: notification/controller/NotificationController.java */
export const notificationService = {
  contactAdmin: (body: ContactAdminRequest) => apiPost<void>('/v1/notifications/contact-admin', body),
};
