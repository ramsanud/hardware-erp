package com.hardware.erp.subscription.dto;

import java.time.LocalDate;
import java.util.List;

public record UsageResponse(LocalDate periodStart, LocalDate periodEnd, String planCode, List<UsageItemResponse> items) {}
