/**
 * Mirrors backend developer/dto/*.java exactly (CR-045).
 *
 * Nothing here is shop data - these types describe the running process, not
 * the business.
 */

/** DeveloperInspectionStatusResponse.java */
export interface DeveloperInspectionStatus {
  /** environmentAllows && permissionHeld. The only field that grants anything. */
  available: boolean;
  environmentAllows: boolean;
  permissionHeld: boolean;
  /** Profile names only. Never a configured value. */
  activeProfiles: string[];
}

/** RuntimeDiagnosticsResponse.java */
export interface RuntimeDiagnostics {
  application: string;
  version: string;
  activeProfiles: string[];
  javaVersion: string;
  osName: string;
  availableProcessors: number;
  heapUsedMb: number;
  heapMaxMb: number;
  uptimeSeconds: number;
  /** ISO-8601 with offset. */
  serverTime: string;
  serverTimeZone: string;
}

/** RequestEchoResponse.java */
export interface RequestEcho {
  method: string;
  path: string;
  requestId: string | null;
  clientIp: string | null;
  userId: number | null;
  tenantId: number | null;
  /** Credential-bearing headers are removed server-side before this arrives. */
  headers: Record<string, string>;
}
