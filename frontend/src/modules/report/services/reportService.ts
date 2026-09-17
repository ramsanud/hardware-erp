import { apiGet, apiGetBlob } from '@/services/apiClient';
import type {
  DayBookReport, Gstr1Document, GstSummaryReport, PurchaseRegisterReport,
  ReceivablesAgeingReport, ReportFormat, StockValuationReport,
} from '../types';

/** Backend: report/controller/ReportController.java (CR-086), report/gst/Gstr1Controller.java (CR-087). */
export const reportService = {
  dayBook: (from: string, to: string) =>
    apiGet<DayBookReport>('/v1/reports/day-book', { params: { from, to } }),
  receivablesAgeing: (asOf?: string) =>
    apiGet<ReceivablesAgeingReport>('/v1/reports/receivables-ageing', { params: asOf ? { asOf } : {} }),
  stockValuation: () => apiGet<StockValuationReport>('/v1/reports/stock-valuation'),
  purchaseRegister: (from: string, to: string) =>
    apiGet<PurchaseRegisterReport>('/v1/reports/purchase-register', { params: { from, to } }),
  gstSummary: (from: string, to: string) =>
    apiGet<GstSummaryReport>('/v1/reports/gst-summary', { params: { from, to } }),

  /** The same figures the screen shows, as a file. `params` are the report's own filters. */
  export: (report: string, format: ReportFormat, params: Record<string, string>) =>
    apiGetBlob(`/v1/reports/${report}/export`, { params: { ...params, format } }),

  gstr1: (period: string) => apiGet<Gstr1Document>('/v1/reports/gstr1', { params: { period } }),
  gstr1File: (period: string) => apiGetBlob('/v1/reports/gstr1/download', { params: { period } }),
};
