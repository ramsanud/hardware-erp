import { apiGet } from '@/services/apiClient';

export interface SalesSummaryResponse {
  totalSalesDisplay: string;
  todaySalesDisplay: string;
  outstandingCustomerBalanceDisplay: string;
  todaySalesPaise: number;
  yesterdaySalesPaise: number;
}

/** Backend: dashboard/controller/DashboardController.java */
export const dashboardService = {
  salesSummary: () => apiGet<SalesSummaryResponse>('/v1/dashboard/sales-summary'),
};
