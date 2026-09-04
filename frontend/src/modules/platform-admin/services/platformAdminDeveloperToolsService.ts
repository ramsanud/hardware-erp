import { platformAdminGet, platformAdminPost } from '@/services/platformAdminApiClient';
import type { BackgroundJobResponse, DatabaseDiagnosticsResponse } from '../types';

export const platformAdminDeveloperToolsService = {
  jobs() {
    return platformAdminGet<BackgroundJobResponse[]>('/v1/platform-admin/developer-tools/jobs');
  },
  retryJob(jobName: string) {
    return platformAdminPost<void>(`/v1/platform-admin/developer-tools/jobs/${encodeURIComponent(jobName)}/retry`);
  },
  database() {
    return platformAdminGet<DatabaseDiagnosticsResponse>('/v1/platform-admin/developer-tools/database');
  },
};
