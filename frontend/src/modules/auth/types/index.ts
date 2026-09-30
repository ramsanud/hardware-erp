/**
 * Mirrors backend auth/dto/*.java exactly.
 * Changing a name here without changing the DTO breaks at runtime, not compile
 * time, so these must be kept in step with the backend records.
 */

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
export type RoleStatus = 'ACTIVE' | 'INACTIVE';

export type AuditAction =
  | 'LOGIN_SUCCESS' | 'LOGIN_FAILURE' | 'ACCOUNT_LOCKED'
  | 'LOGOUT' | 'LOGOUT_ALL' | 'SESSION_REVOKED'
  | 'TOKEN_REFRESHED' | 'REFRESH_TOKEN_REUSE_DETECTED'
  | 'PASSWORD_CHANGED' | 'PASSWORD_RESET_REQUESTED' | 'PASSWORD_RESET'
  | 'PASSWORD_RESET_BY_ADMIN'
  | 'USER_CREATED' | 'USER_UPDATED' | 'USER_DEACTIVATED' | 'USER_RESTORED'
  | 'ROLE_CHANGED' | 'ROLE_CREATED' | 'ROLE_UPDATED' | 'ROLE_DELETED'
  | 'RATE_LIMIT_EXCEEDED' | 'BOOTSTRAP_OWNER_CREATED' | 'BANK_ACCOUNT_REVEALED';

// ---- requests ----

export interface LoginRequest {
  identifier: string;
  password: string;
  /** Cloudflare Turnstile token. Sent only when the server says a check is enabled. */
  captchaToken?: string | null;
}

export interface CaptchaConfigResponse {
  enabled: boolean;
  siteKey: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ForgotPasswordRequest {
  identifier: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface ResetUserPasswordRequest {
  newPassword: string;
}

export interface UpdateProfileRequest {
  fullName: string;
  email?: string | null;
  /** CR-078. Required only when email is changing and the CURRENT address was verified - a takeover step. From /step-up/verify. */
  stepUpToken?: string | null;
}

export interface CreateUserRequest {
  fullName: string;
  mobileNo: string;
  email?: string | null;
  employeeCode?: string | null;
  roleId: number;
  password: string;
  mustChangePassword: boolean;
}

export interface UpdateUserRequest {
  fullName: string;
  mobileNo: string;
  email?: string | null;
  employeeCode?: string | null;
  roleId: number;
  status: UserStatus;
}

export interface RoleRequest {
  code: string;
  name: string;
  description?: string | null;
  permissions: string[];
  status: RoleStatus;
}

// ---- responses ----

export interface UserResponse {
  id: number;
  fullName: string;
  mobileNo: string;
  email?: string | null;
  employeeCode?: string | null;
  roleId: number;
  roleCode: string;
  roleName: string;
  permissions: string[];
  status: UserStatus;
  mustChangePassword: boolean;
  /** BUG-FE-039. True only when /me/avatar would answer 200; the shell asks for the image only then. */
  hasAvatar: boolean;
  /** CR-078. Whether an authenticator app is enrolled - the profile offers "set one up" when false. */
  mfaEnabled: boolean;
  /** CR-078. Whether a code sent to `email` was ever entered correctly. False for a blank email. */
  emailVerified: boolean;
  lastLoginAt?: string | null;
  createdAt?: string | null;
}

/** CR-053 backlog item 6. One row per business change this user made, newest first. */
export interface UserActivityResponse {
  id: number;
  moduleCode: string;
  entityType: string;
  entityId?: number | null;
  entityLabel?: string | null;
  action: string;
  remarks?: string | null;
  createdAt: string;
}

export interface LoginResponse {
  accessToken: string;
  /** Null in cookie transport, which is the default. */
  refreshToken?: string | null;
  tokenType: string;
  expiresInSeconds: number;
  mustChangePassword: boolean;
  user: UserResponse;
}
/**
 * CR-058 - what POST /v1/auth/login now returns. A correct password proves
 * only the first factor; the session arrives from /mfa/verify or
 * /mfa/enroll/confirm.
 */
export interface LoginChallengeResponse {
  /** Null when MFA is disabled server-side (CR-060) - there is no challenge to identify. */
  mfaToken: string | null;
  /** True for an account that has no authenticator app AND no email fallback available - it must enroll before it can sign in. */
  enrollmentRequired: boolean;
  expiresInSeconds: number;
  /**
   * CR-078. How the second factor is collected: TOTP (authenticator app or
   * backup code) or EMAIL (a code sent to emailHint). Null when enrolling or
   * when MFA is disabled.
   */
  mfaMethod: 'TOTP' | 'EMAIL' | null;
  /** CR-078. The masked address a sign-in code went to, e.g. o***r@sarahardware.in. Null unless mfaMethod is EMAIL. */
  emailHint: string | null;
  /**
   * CR-060 - the completed session, present ONLY when the server has
   * app.security.mfa-required=false and the password was therefore the only
   * factor. Null whenever a second factor is being demanded.
   *
   * Exactly one of `session` and `mfaToken` is ever populated, so the caller
   * branches on this one field rather than inferring the mode from config the
   * browser cannot see.
   */
  session: LoginResponse | null;
}

/** CR-078. Acknowledges a code sent by email - resend, step-up or registration - and says where it went, masked. */
export interface OtpSentResponse {
  emailHint: string;
  /** Seconds before another code may be requested. */
  resendAfterSeconds: number;
}

/** CR-078. Proof a signed-in user just re-confirmed by email code. Presented as stepUpToken to the endpoints that demand it. */
export interface StepUpResponse {
  stepUpToken: string;
  expiresInSeconds: number;
}



export interface MfaEnrollResponse {
  otpAuthUri: string;
  /** PNG bytes, base64 - render as data:image/png;base64,... */
  qrCodePngBase64: string;
  /** Manual-entry fallback for an app that cannot scan a QR code. */
  secretBase32: string;
}

export interface MfaConfirmResponse {
  session: LoginResponse;
  /** One-time recovery codes. Shown exactly once, here. */
  backupCodes: string[];
}

export interface RoleResponse {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  systemRole: boolean;
  status: RoleStatus;
  permissions: string[];
  userCount: number;
}

export interface PermissionResponse {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  moduleCode: string;
  displayOrder: number;
}

export interface PermissionGroupResponse {
  moduleCode: string;
  permissions: PermissionResponse[];
}

export interface SessionResponse {
  id: number;
  ipAddress?: string | null;
  userAgent?: string | null;
  createdAt?: string | null;
  lastUsedAt?: string | null;
  expiresAt?: string | null;
  current: boolean;
}

export interface SecurityAuditLogResponse {
  id: number;
  action: AuditAction;
  entityType?: string | null;
  entityId?: number | null;
  userId?: number | null;
  fullName?: string | null;
  success: boolean;
  failureReason?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  requestId?: string | null;
  createdAt: string;
}

// ---- query params ----

/**
 * CR-058 - the recycle-bin projection. Like UserResponse it never carries
 * security state (token version, failed attempts, lockout).
 */
export interface UserDeletedResponse {
  id: number;
  fullName: string;
  mobileNo: string;
  employeeCode?: string | null;
  roleName?: string | null;
  deletedAt: string;
}

export interface UserSearchParams {
  search?: string;
  status?: UserStatus;
  roleId?: number;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

export interface AuditSearchParams {
  userId?: number;
  action?: AuditAction;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}
