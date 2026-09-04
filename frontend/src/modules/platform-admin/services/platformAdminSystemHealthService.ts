import { platformAdminGet, platformAdminPost } from '@/services/platformAdminApiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  IncidentSeverity, IncidentStatus, PlatformIncidentResponse, PlatformServiceName, SystemHealthResponse,
} from '../types';

export const platformAdminSystemHealthService = {
  overview() {
    return platformAdminGet<SystemHealthResponse>('/v1/platform-admin/system-health');
  },
};

export interface IncidentListParams {
  service?: PlatformServiceName;
  status?: IncidentStatus;
  severity?: IncidentSeverity;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const platformAdminIncidentService = {
  list(params: IncidentListParams) {
    return platformAdminGet<PageResponse<PlatformIncidentResponse>>('/v1/platform-admin/incidents', { params });
  },
  markInvestigating(id: number) {
    return platformAdminPost<PlatformIncidentResponse>(`/v1/platform-admin/incidents/${id}/investigating`);
  },
  resolve(id: number) {
    return platformAdminPost<PlatformIncidentResponse>(`/v1/platform-admin/incidents/${id}/resolve`);
  },
  ignore(id: number) {
    return platformAdminPost<PlatformIncidentResponse>(`/v1/platform-admin/incidents/${id}/ignore`);
  },
  reopen(id: number) {
    return platformAdminPost<PlatformIncidentResponse>(`/v1/platform-admin/incidents/${id}/reopen`);
  },
};
