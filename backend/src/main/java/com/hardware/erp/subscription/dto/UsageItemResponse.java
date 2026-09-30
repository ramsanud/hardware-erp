package com.hardware.erp.subscription.dto;

public record UsageItemResponse(String usageKey, String label, long usedCount, long includedCount, long remainingCount, boolean limitReached) {}
