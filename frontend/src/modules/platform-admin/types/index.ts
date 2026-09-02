/**
 * Mirrors the backend DTOs exactly (com.hardware.erp.platformadmin.dto.*).
 * A completely separate type surface from modules/auth/types - see
 * platformAdminApiClient.ts for why the two consoles never share code.
 */

export type PlatformAdminRole =
  | 'SUPER_ADMIN'
  | 'PLATFORM_ADMIN'
  | 'SUPPORT_ADMIN'
  | 'SECURITY_ADMIN'
  | 'FINANCE_ADMIN'
  | 'DEVELOPER'
  | 'READ_ONLY_AUDITOR';

export interface PlatformAdminLoginRequest {
  email: string;
  password: string;
}

export interface PlatformAdminLoginChallengeResponse {
  mfaToken: string;
  enrollmentRequired: boolean;
  expiresInSeconds: number;
}

export interface PlatformAdminMfaVerifyRequest {
  mfaToken: string;
  code: string;
}

export interface PlatformAdminMfaEnrollResponse {
  otpAuthUri: string;
  qrCodePngBase64: string;
  secretBase32: string;
}

export interface PlatformAdminResponse {
  id: number;
  fullName: string;
  email: string;
  role: PlatformAdminRole;
  permissions: string[];
  mfaEnabled: boolean;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface PlatformAdminSessionResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  admin: PlatformAdminResponse;
}

export interface PlatformAdminMfaConfirmResponse {
  session: PlatformAdminSessionResponse;
  backupCodes: string[];
}

export interface CreatePlatformAdminRequest {
  fullName: string;
  email: string;
  password: string;
  role: PlatformAdminRole;
}
