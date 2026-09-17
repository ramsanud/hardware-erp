package com.hardware.erp.report.service;

import com.hardware.erp.report.dto.ReportDtos.DayBookReport;
import com.hardware.erp.report.dto.ReportDtos.GstSummaryReport;
import com.hardware.erp.report.dto.ReportDtos.PurchaseRegisterReport;
import com.hardware.erp.report.dto.ReportDtos.ReceivablesAgeingReport;
import com.hardware.erp.report.dto.ReportDtos.StockValuationReport;

import java.time.LocalDate;

/** CR-086. Every method reads the current tenant from the security context, never from a parameter. */
public interface ReportService {

    DayBookReport dayBook(LocalDate from, LocalDate to);

    ReceivablesAgeingReport receivablesAgeing(LocalDate asOf);

    StockValuationReport stockValuation();

    PurchaseRegisterReport purchaseRegister(LocalDate from, LocalDate to);

    GstSummaryReport gstSummary(LocalDate from, LocalDate to);
}
