package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.ChurnPoint;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.GrowthPoint;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.ModuleUsagePoint;
import com.hardware.erp.platformadmin.service.impl.TenantAnalyticsExportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Renders all three formats for real rather than asserting on a mock.
 *
 * The PDF case exists because of a genuine defect this test would have caught
 * and code review did not: openhtmltopdf parses strict XHTML, so the named
 * HTML entity {@code &divide;} in the churn caption was an undeclared-entity
 * parse error that failed the whole render with a 500. It was found by
 * PlatformAdminAnalyticsControllerIT, which needs Docker; this test pins the
 * same behaviour at unit speed so a future edit to the HTML cannot reintroduce
 * it silently on a machine with no Docker available.
 */
@ExtendWith(MockitoExtension.class)
class TenantAnalyticsExportServiceImplTest {

    @Mock private TenantAnalyticsService analyticsService;

    private TenantAnalyticsExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TenantAnalyticsExportServiceImpl(analyticsService);
        when(analyticsService.overview()).thenReturn(new TenantAnalyticsResponse(
                7L,
                List.of(new GrowthPoint("2026-08", 2, 5, 4), new GrowthPoint("2026-09", 1, 3, 6)),
                List.of(new ModuleUsagePoint("Products", 5, 71.4), new ModuleUsagePoint("Invoices", 3, 42.9)),
                // One month with a real rate, one with null - the "no tenants yet to
                // divide by" case must render as a dash, never as a fabricated 0%.
                List.of(new ChurnPoint("2026-08", 1, 20, 5.0), new ChurnPoint("2026-09", 0, 0, null))));
    }

    @Test
    @DisplayName("PDF export renders real PDF bytes - no named HTML entity breaks the XHTML parse")
    void pdfExportRendersRealPdf() {
        byte[] pdf = service.exportPdf();

        assertThat(pdf).isNotEmpty();
        // %PDF- magic number: proves openhtmltopdf actually completed a render,
        // not that a stub returned something.
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("XLSX export renders a real workbook")
    void xlsxExportRendersWorkbook() {
        byte[] xlsx = service.exportXlsx();

        assertThat(xlsx).isNotEmpty();
        // "PK" - xlsx is a zip container.
        assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
    }

    @Test
    @DisplayName("CSV export carries all three sections and renders a null churn rate as blank, never 0")
    void csvExportCarriesAllSections() {
        String csv = new String(service.exportCsv(), StandardCharsets.UTF_8);

        assertThat(csv).contains("Tenant Growth", "Module Usage", "Churn");
        assertThat(csv).contains("2026-08", "Products", "Invoices");
        // The null-churn month must not claim 0.00 - see TenantAnalyticsResponse's javadoc.
        assertThat(csv).doesNotContain("0.00");
    }
}
