/** Backend: document/dto/ReportJobDtos.java (CR-101). */

export type ReportJobFormat = 'PDF' | 'XLSX' | 'CSV' | 'PNG' | 'JSON';
export type ReportJobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface ReportJob {
  id: number;
  reportType: string;
  format: ReportJobFormat;
  status: ReportJobStatus;
  fileName?: string | null;
  fileSizeBytes?: number | null;
  errorMessage?: string | null;
  createdAt: string;
  completedAt?: string | null;
}

export interface ReportJobRequest {
  reportType: string;
  format: ReportJobFormat;
  params: Record<string, string>;
}

/** The share channels a finished document can leave by. Download needs no server call beyond the file itself. */
export type ShareChannel = 'DOWNLOAD' | 'WHATSAPP' | 'EMAIL';

/** What the share modal can build. XLSX/CSV are downloads only - a spreadsheet is not something a chat image can carry. */
export type ShareFormat = 'PDF' | 'PNG' | 'XLSX';
