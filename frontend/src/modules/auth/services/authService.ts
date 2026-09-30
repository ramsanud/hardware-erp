import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type {
  CaptchaConfigResponse,
  ChangePasswordRequest, ForgotPasswordRequest, LoginChallengeResponse, LoginRequest,
  LoginResponse, MfaConfirmResponse, MfaEnrollResponse, OtpSentResponse,
  ResetPasswordRequest, SessionResponse, StepUpResponse, UpdateProfileRequest, UserResponse,
} from '../types';

/** Backend: auth/controller/AuthController.java */
export const authService = {
  /** CR-058 - returns an MFA challenge, never a session. */
  login: (body: LoginRequest) => apiPost<LoginChallengeResponse>('/v1/auth/login', body),

  /** Begins mandatory MFA enrollment for an account that has never set it up. */
  enrollMfa: (mfaToken: string) =>
    apiPost<MfaEnrollResponse>('/v1/auth/mfa/enroll', { mfaToken }),

  confirmMfaEnroll: (mfaToken: string, code: string) =>
    apiPost<MfaConfirmResponse>('/v1/auth/mfa/enroll/confirm', { mfaToken, code }),

  /** Completes sign-in with an authenticator code, a backup code, or - when the challenge is EMAIL - the code sent there. */
  verifyMfa: (mfaToken: string, code: string) =>
    apiPost<LoginResponse>('/v1/auth/mfa/verify', { mfaToken, code }),

  /** CR-078 - another sign-in code to the same address; refused with 429 inside the 60-second cooldown. */
  resendEmailCode: (mfaToken: string) =>
    apiPost<OtpSentResponse>('/v1/auth/mfa/email/resend', { mfaToken }),

  /** Public - the sign-in page needs this before anyone has signed in. */
  captchaConfig: () => apiGet<CaptchaConfigResponse>('/v1/auth/captcha-config'),

  refresh: () => apiPost<LoginResponse>('/v1/auth/refresh', {}),

  /** Revokes only this device's session. Other devices stay signed in. */
  logout: () => apiPost<void>('/v1/auth/logout', {}),

  logoutAll: () => apiPost<void>('/v1/auth/logout-all', {}),

  me: () => apiGet<UserResponse>('/v1/auth/me'),

  updateProfile: (body: UpdateProfileRequest) =>
    apiPut<UserResponse>('/v1/auth/me', body),

  changePassword: (body: ChangePasswordRequest) =>
    apiPost<void>('/v1/auth/change-password', body),

  forgotPassword: (body: ForgotPasswordRequest) =>
    apiPost<void>('/v1/auth/forgot-password', body),

  resetPassword: (body: ResetPasswordRequest) =>
    apiPost<void>('/v1/auth/reset-password', body),

  /** CR-078 - the code path of forgot-password, for a phone where the emailed link opens the wrong browser. */
  resetPasswordWithCode: (body: { identifier: string; code: string; newPassword: string }) =>
    apiPost<void>('/v1/auth/reset-password/code', body),

  /** CR-078 - sends a code to the signed-in user's CURRENT verified email, ahead of a sensitive change. */
  sendStepUpCode: () => apiPost<OtpSentResponse>('/v1/auth/step-up/send', {}),

  /** CR-078 - exchanges a correct step-up code for a short-lived stepUpToken. */
  verifyStepUp: (code: string) => apiPost<StepUpResponse>('/v1/auth/step-up/verify', { code }),

  /** CR-078 - adds an authenticator app to an account that has been signing in with email codes. */
  beginMfaSetup: () => apiPost<MfaEnrollResponse>('/v1/auth/mfa/setup', {}),

  /** CR-078 - confirms beginMfaSetup and returns ten backup codes, shown exactly once. */
  confirmMfaSetup: (code: string) => apiPost<string[]>('/v1/auth/mfa/setup/confirm', { code }),

  sessions: () => apiGet<SessionResponse[]>('/v1/auth/sessions'),

  revokeSession: (id: number) => apiDelete(`/v1/auth/sessions/${id}`),
};
