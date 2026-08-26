import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type {
  CaptchaConfigResponse,
  ChangePasswordRequest, ForgotPasswordRequest, LoginRequest, LoginResponse,
  ResetPasswordRequest, SessionResponse, UpdateProfileRequest, UserResponse,
} from '../types';

/** Backend: auth/controller/AuthController.java */
export const authService = {
  login: (body: LoginRequest) => apiPost<LoginResponse>('/v1/auth/login', body),

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
