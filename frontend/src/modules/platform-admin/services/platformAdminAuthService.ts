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

  /** CR-065 - the browser supplies the refresh cookie; there is no token to pass. */
  refresh() {
    return platformAdminPost<PlatformAdminSessionResponse>(
      '/v1/platform-admin/auth/refresh', {});
  },

  logout() {
    return platformAdminPost<void>('/v1/platform-admin/auth/logout', {});
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
