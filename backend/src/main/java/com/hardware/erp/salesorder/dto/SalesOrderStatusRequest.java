package com.hardware.erp.salesorder.dto;

import com.hardware.erp.salesorder.entity.SalesOrderStatus;
import jakarta.validation.constraints.NotNull;

public record SalesOrderStatusRequest(@NotNull SalesOrderStatus status) {}
