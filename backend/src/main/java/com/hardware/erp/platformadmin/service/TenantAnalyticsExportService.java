package com.hardware.erp.platformadmin.service;

public interface TenantAnalyticsExportService {

    byte[] exportCsv();

    byte[] exportXlsx();

    byte[] exportPdf();
}
