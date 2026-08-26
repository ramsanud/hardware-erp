package com.hardware.erp.quotation.dto;

import com.hardware.erp.quotation.entity.QuotationStatus;
import jakarta.validation.constraints.NotNull;

public record QuotationStatusRequest(@NotNull QuotationStatus status) {}
