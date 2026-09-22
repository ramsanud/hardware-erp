package com.hardware.erp.insights.service;

import com.hardware.erp.insights.dto.InsightsDtos.BoughtTogetherResponse;
import com.hardware.erp.insights.dto.InsightsDtos.DemandTrendResponse;
import com.hardware.erp.insights.dto.InsightsDtos.OverstockResponse;
import com.hardware.erp.insights.dto.InsightsDtos.PricingInsightResponse;
import com.hardware.erp.insights.dto.InsightsDtos.ReorderResponse;
import com.hardware.erp.insights.dto.InsightsDtos.SlowMovingResponse;

/**
 * CR-092 Smart Insights (PREMIUM, FeatureKey.SMART_INSIGHTS, REPORT_VIEW).
 * Every method takes a window in days ending today and reads only what the
 * shop recorded in it.
 */
public interface InsightsService {

    SlowMovingResponse slowMoving(int days);

    OverstockResponse overstock(int days, int coverThresholdDays);

    ReorderResponse reorder(int days, int leadTimeDays);

    DemandTrendResponse demandTrend(int days);

    BoughtTogetherResponse boughtTogether(int days);

    PricingInsightResponse pricing(int days);
}
