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
  | 'USER_CREATED' | 'USER_UPDATED' | 'USER_DEACTIVATED'
  | 'ROLE_CHANGED' | 'ROLE_CREATED' | 'ROLE_UPDATED' | 'ROLE_DELETED'
  | 'RATE_LIMIT_EXCEEDED' | 'BOOTSTRAP_OWNER_CREATED';

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
