/**
 * CR-072. Mirrors backend common/activity/ActivityLogResponse.java exactly.
 *
 * `tenantId` is deliberately absent here as it is there: the caller can only
 * ever read their own shop's history, so echoing the id back says nothing.
 */
export type ActivityAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'IMPORT';

export interface ActivityLogResponse {
  id: number;
  moduleCode: string;
  entityType: string;
  entityId: number | null;
  /** The record's name AS IT WAS at the time - it may since have been renamed. */
  entityLabel: string | null;
  action: ActivityAction;
  /** Only the fields that changed, never the whole row. Already redacted server-side. */
  oldValues: Record<string, unknown> | null;
  newValues: Record<string, unknown> | null;
  userId: number | null;
  /** Snapshotted at the time. "SYSTEM" for a scheduled job. */
  fullName: string | null;
  roleCode: string | null;
  ipAddress: string | null;
  requestId: string | null;
  remarks: string | null;
  createdAt: string;
}

export interface ActivityLogSearchParams {
  moduleCode?: string;
  entityType?: string;
  entityId?: number;
  userId?: number;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}
