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

// ---------------------------------------------------------------
// Phase 2 - Tenant Management + Overview. Mirrors
// com.hardware.erp.platformadmin.dto.{PlatformDashboardResponse,
// PlatformTenantSummaryResponse,PlatformTenantDetailResponse}.
// ---------------------------------------------------------------

export type TenantStatus = 'ACTIVE' | 'SUSPENDED';
export type SubscriptionTier = 'FREE' | 'PRO' | 'MAX';

export interface PlatformDashboardResponse {
  tenants: {
    total: number;
    active: number;
    suspended: number;
    newThisMonth: number;
    growthPercentVsLastMonth: number | null;
  };
  users: {
    total: number;
    active: number;
    newToday: number;
  };
  businessActivityToday: {
    invoices: number;
    payments: number;
    purchases: number;
  };
  subscriptions: {
    free: number;
    pro: number;
    max: number;
  };
  platformHealth: {
    databaseReachable: boolean;
  };
  generatedAt: string;
}

export interface PlatformTenantSummaryResponse {
  id: number;
  name: string;
  slug: string;
  ownerName: string | null;
  ownerEmail: string | null;
  phone: string | null;
  email: string | null;
  subscriptionTier: SubscriptionTier;
  status: TenantStatus;
  createdAt: string;
  lastActiveAt: string | null;
  userCount: number;
}

export interface PlatformTenantDetailResponse {
  id: number;
  name: string;
  slug: string;
  ownerName: string | null;
  ownerEmail: string | null;
  phone: string | null;
  email: string | null;
  city: string | null;
  stateCode: string | null;
  subscriptionTier: SubscriptionTier;
  subscriptionTrialExpiresAt: string | null;
  status: TenantStatus;
  createdAt: string;
  lastActiveAt: string | null;
  usage: {
    users: number;
    customers: number;
    products: number;
    invoices: number;
    purchases: number;
    payments: number;
    expenses: number;
  };
  whatsAppConnectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'NEEDS_ATTENTION' | null;
}

// ---------------------------------------------------------------
// Phase 3 - System Health + Incidents. Mirrors
// com.hardware.erp.platformadmin.dto.{SystemHealthResponse,PlatformIncidentResponse}.
// ---------------------------------------------------------------

export type PlatformServiceName =
  | 'BACKEND' | 'DATABASE' | 'AUTHENTICATION' | 'STORAGE' | 'WHATSAPP' | 'EMAIL' | 'BACKGROUND_JOBS';

export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';

export interface ServiceHealth {
  service: PlatformServiceName;
  status: HealthStatus;
  responseTimeMs: number | null;
  detail: string | null;
  lastCheckedAt: string | null;
  lastFailureAt: string | null;
  errorCount: number;
}

export interface SystemHealthResponse {
  services: ServiceHealth[];
  generatedAt: string;
}

export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'IGNORED';

export interface PlatformIncidentResponse {
  id: number;
  service: PlatformServiceName;
  severity: IncidentSeverity;
  title: string;
  description: string | null;
  status: IncidentStatus;
  firstSeen: string;
  lastSeen: string;
  occurrenceCount: number;
  resolvedAt: string | null;
  resolvedBy: number | null;
}

// ---------------------------------------------------------------
// Phase 4 - Support Center. Mirrors com.hardware.erp.supportticket.dto.*
// (the platform-admin view of the same tenant-facing ticket model).
// ---------------------------------------------------------------

export type TicketCategory =
  | 'LOGIN' | 'INVOICE' | 'PAYMENT' | 'PURCHASE' | 'INVENTORY'
  | 'WHATSAPP' | 'SUBSCRIPTION' | 'TECHNICAL' | 'OTHER';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_FOR_USER' | 'RESOLVED' | 'CLOSED';
export type MessageAuthorType = 'TENANT_USER' | 'PLATFORM_ADMIN';

export interface TicketMessageResponse {
  id: number;
  authorType: MessageAuthorType;
  authorName: string;
  message: string;
  internal: boolean;
  createdAt: string;
}

export interface SupportTicketSummaryResponse {
  id: number;
  tenantName: string | null;
  subject: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  assignedAdminId: number | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface SupportTicketDetailResponse {
  id: number;
  tenantName: string;
  raisedByName: string;
  subject: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  assignedAdminId: number | null;
  createdAt: string;
  updatedAt: string | null;
  resolvedAt: string | null;
  messages: TicketMessageResponse[];
}

// ---------------------------------------------------------------
// Phase 6 - Global Audit Log viewer.
// ---------------------------------------------------------------

export type PlatformAuditAction = string;

export interface PlatformAuditLogResponse {
  id: number;
  adminId: number | null;
  adminEmail: string | null;
  action: PlatformAuditAction;
  success: boolean;
  targetType: string | null;
  targetId: number | null;
  detail: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------
// Phase 7 - Developer Tools.
// ---------------------------------------------------------------

export type JobExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface BackgroundJobResponse {
  jobName: string;
  lastStatus: JobExecutionStatus;
  lastRunAt: string;
  lastDurationMs: number | null;
  lastDetail: string | null;
  retryable: boolean;
}

export interface DatabaseDiagnosticsResponse {
  connectionReachable: boolean;
  pingMs: number | null;
  pool: { active: number; idle: number; total: number; maxSize: number } | null;
  migrationVersion: string | null;
  appliedMigrationCount: number;
  migrationsPending: boolean;
}

// ---------------------------------------------------------------
// Phase 6 - Security Center.
// ---------------------------------------------------------------

export interface PlatformAdminActiveSessionResponse {
  id: number;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  current: boolean;
}

export interface PlatformSecurityDashboardResponse {
  failedLoginsToday: number;
  mfaChallengeFailuresToday: number;
  accountsLockedToday: number;
  totalAdmins: number;
  adminsWithMfaEnabled: number;
  activeSessions: number;
  recentPrivilegedActions: PlatformAuditLogResponse[];
}

// ---------------------------------------------------------------
// Phase 8 - Feature Flags.
// ---------------------------------------------------------------

export type FeatureFlagScope = 'GLOBAL' | 'TENANT' | 'PLAN';

export interface FeatureFlagResponse {
  id: number;
  flagKey: string;
  name: string;
  description: string | null;
  enabled: boolean;
  scope: FeatureFlagScope;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateFeatureFlagRequest {
  flagKey: string;
  name: string;
  description: string | null;
  scope: FeatureFlagScope;
}

export interface PlatformSupportDashboardResponse {
  open: number;
  inProgress: number;
  waitingForUser: number;
  highPriorityOrUrgent: number;
  assignedToMe: number;
  resolvedToday: number;
}
