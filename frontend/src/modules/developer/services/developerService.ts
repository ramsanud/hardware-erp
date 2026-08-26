import { apiGet } from '@/services/apiClient';
import type { DeveloperInspectionStatus, RequestEcho, RuntimeDiagnostics } from '../types';

/**
 * Backend: developer/DeveloperInspectionController.java
 *
 * Every call here answers 403 unless the environment permits inspection AND
 * the signed-in user holds DEVELOPER_INSPECT. That is the enforcement point -
 * hiding the page is only so nobody clicks into a guaranteed error.
 */
export const developerService = {
  /** Readable by any signed-in user, so the page can say WHICH gate closed. */
  status: () => apiGet<DeveloperInspectionStatus>('/v1/dev/inspection/status'),

  runtime: () => apiGet<RuntimeDiagnostics>('/v1/dev/inspection/runtime'),

  requestEcho: () => apiGet<RequestEcho>('/v1/dev/inspection/request-echo'),
};
