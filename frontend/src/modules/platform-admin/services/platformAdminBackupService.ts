import { platformAdminGet, platformAdminPostBlob } from '@/services/platformAdminApiClient';
import type { TenantExportFormat, TenantExportLogResponse } from '../types';

/** Backend: platformadmin/controller/PlatformAdminBackupController.java (CR-057 phase 11) */
export const platformAdminBackupService = {
  history: (tenantId: number) =>
    platformAdminGet<TenantExportLogResponse[]>(`/v1/platform-admin/tenants/${tenantId}/backups`),

  export: (tenantId: number, format: TenantExportFormat) =>
    platformAdminPostBlob(`/v1/platform-admin/tenants/${tenantId}/backups`, { params: { format } }),
};
