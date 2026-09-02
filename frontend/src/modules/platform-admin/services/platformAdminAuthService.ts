import { platformAdminGet, platformAdminPost } from '@/services/platformAdminApiClient';
import type {
  CreatePlatformAdminRequest,
  PlatformAdminLoginChallengeResponse,
  PlatformAdminLoginRequest,
  PlatformAdminMfaConfirmResponse,
  PlatformAdminMfaEnrollResponse,
  PlatformAdminMfaVerifyRequest,
  PlatformAdminResponse,
  PlatformAdminSessionResponse,
} from '../types';

export const platformAdminAuthService = {
  login(body: PlatformAdminLoginRequest) {
    return platformAdminPost<PlatformAdminLoginChallengeResponse>(
      '/v1/platform-admin/auth/login', body);
  },

  verifyMfa(body: PlatformAdminMfaVerifyRequest) {
    return platformAdminPost<PlatformAdminSessionResponse>(
      '/v1/platform-admin/auth/mfa/verify', body);
  },

  enroll(mfaToken: string) {
    return platformAdminPost<PlatformAdminMfaEnrollResponse>(
      '/v1/platform-admin/auth/mfa/enroll', { mfaToken });
  },

  confirmEnroll(body: PlatformAdminMfaVerifyRequest) {
    return platformAdminPost<PlatformAdminMfaConfirmResponse>(
      '/v1/platform-admin/auth/mfa/enroll/confirm', body);
  },

  refresh(refreshToken: string) {
    return platformAdminPost<PlatformAdminSessionResponse>(
      '/v1/platform-admin/auth/refresh', { refreshToken });
  },

  logout(refreshToken: string | null) {
    return platformAdminPost<void>('/v1/platform-admin/auth/logout', { refreshToken });
  },

  logoutAll() {
    return platformAdminPost<void>('/v1/platform-admin/auth/logout-all');
  },

  me() {
    return platformAdminGet<PlatformAdminResponse>('/v1/platform-admin/auth/me');
  },
};

export const platformAdminUserService = {
  create(body: CreatePlatformAdminRequest) {
    return platformAdminPost<PlatformAdminResponse>('/v1/platform-admin/admins', body);
  },

  list() {
    return platformAdminGet<PlatformAdminResponse[]>('/v1/platform-admin/admins');
  },
};
