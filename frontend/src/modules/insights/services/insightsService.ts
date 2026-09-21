import { apiGet } from '@/services/apiClient';

/** Backend: insights/dto/InsightsDtos.java (CR-092). Every figure is a count, sum or ratio over recorded rows. */

export interface InsightWindow { from: string; to: string; days: number }

export interface SlowMovingItem {
  productId: number; productCode: string; productName: string; unit: string;
  quantityOnHand: number; quantitySold: number; lastSoldOn?: string | null;
  stockValuePaise: number; stockValueDisplay: string;
}
export interface SlowMovingResponse { window: InsightWindow; items: SlowMovingItem[]; summary: string }

export interface OverstockItem {
  productId: number; productCode: string; productName: string; unit: string;
  quantityOnHand: number; averageDailySales: number; daysOfCover?: number | null;
  stockValuePaise: number; stockValueDisplay: string;
}
export interface OverstockResponse { window: InsightWindow; coverThresholdDays: number; items: OverstockItem[]; summary: string }

export interface ReorderSuggestion {
  productId: number; productCode: string; productName: string; unit: string;
  quantityOnHand: number; reorderLevel: number; averageDailySales: number; daysOfCover?: number | null;
  suggestedQuantity: number; reason: string;
}
export interface ReorderResponse { window: InsightWindow; leadTimeDays: number; items: ReorderSuggestion[]; summary: string }

export interface DemandTrendItem {
  productId: number; productCode: string; productName: string; unit: string;
  currentQuantity: number; previousQuantity: number; changePercent?: number | null;
}
export interface DemandTrendResponse { current: InsightWindow; previous: InsightWindow; rising: DemandTrendItem[]; falling: DemandTrendItem[]; summary: string }

export interface BoughtTogetherPair {
  productAId: number; productAName: string; productBId: number; productBName: string;
  invoicesTogether: number; invoicesWithA: number; supportPercent?: number | null;
}
export interface BoughtTogetherResponse { window: InsightWindow; pairs: BoughtTogetherPair[]; summary: string }

export interface PricingInsightItem {
  productId: number; productCode: string; productName: string;
  sellingPricePaise: number; sellingPriceDisplay: string;
  averageCostPaise: number; averageCostDisplay: string; marginPercent: number;
  averageRealisedPaise: number; averageRealisedDisplay: string;
  flag: 'SELLING_BELOW_COST' | 'LOW_MARGIN' | 'HEAVILY_DISCOUNTED';
}
export interface PricingInsightResponse { window: InsightWindow; lowMarginThresholdPercent: number; items: PricingInsightItem[]; summary: string }

export const insightsService = {
  slowMoving: (days: number) => apiGet<SlowMovingResponse>('/v1/insights/slow-moving', { params: { days } }),
  overstock: (days: number, coverDays: number) => apiGet<OverstockResponse>('/v1/insights/overstock', { params: { days, coverDays } }),
  reorder: (days: number, leadTimeDays: number) => apiGet<ReorderResponse>('/v1/insights/reorder', { params: { days, leadTimeDays } }),
  demandTrend: (days: number) => apiGet<DemandTrendResponse>('/v1/insights/demand-trend', { params: { days } }),
  boughtTogether: (days: number) => apiGet<BoughtTogetherResponse>('/v1/insights/bought-together', { params: { days } }),
  pricing: (days: number) => apiGet<PricingInsightResponse>('/v1/insights/pricing', { params: { days } }),
};
