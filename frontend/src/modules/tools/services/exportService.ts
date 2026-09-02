import { apiGetBlob } from '@/services/apiClient';

/** Backend: export/TallyExportController.java (CR-053 backlog item 2) */
export const exportService = {
  tallyXml: (fromDate: string, toDate: string) =>
    apiGetBlob('/v1/exports/tally', { params: { fromDate, toDate } }),
};
