import { apiPost } from '@/services/apiClient';

export type DiagnosticStatus = 'SENT' | 'LOGGED_ONLY' | 'FAILED' | 'DELIVERED' | 'READ';

/** Backend: notification/dto/MailDiagnosticResponse.java */
export interface MailDiagnosticResponse {
  status: DiagnosticStatus;
  fromAddress: string | null;
  toAddress: string;
  detail: string;
}

/** Backend: notification/dto/SmsDiagnosticResponse.java (CR-085) */
export interface SmsDiagnosticResponse {
  status: DiagnosticStatus;
  toMobileNo: string;
  providerMessageId: string | null;
  detail: string;
}

/**
 * Backend: notification/controller/MailDiagnosticController.java and
 * SmsDiagnosticController.java. Both send one real message through the same
 * path production messages take and report the provider's own verdict.
 */
export const messagingDiagnosticService = {
  testEmail: (toEmail: string) =>
    apiPost<MailDiagnosticResponse>('/v1/settings/mail/test', undefined, { params: { toEmail } }),

  testSms: (toMobileNo: string) =>
    apiPost<SmsDiagnosticResponse>('/v1/settings/sms/test', undefined, { params: { toMobileNo } }),
};
