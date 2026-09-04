import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type {
  CaptchaConfigResponse,
  ChangePasswordRequest, ForgotPasswordRequest, LoginChallengeResponse, LoginRequest,
  LoginResponse, MfaConfirmResponse, MfaEnrollResponse,
  ResetPasswordRequest, SessionResponse, UpdateProfileRequest, UserResponse,
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

  /** Completes sign-in with an authenticator code or a one-time backup code. */
  verifyMfa: (mfaToken: string, code: string) =>
    apiPost<LoginResponse>('/v1/auth/mfa/verify', { mfaToken, code }),

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

  sessions: () => apiGet<SessionResponse[]>('/v1/auth/sessions'),

  revokeSession: (id: number) => apiDelete(`/v1/auth/sessions/${id}`),
};
