package com.hardware.erp.report.service.impl;

import com.hardware.erp.report.dto.ReportDtos.GstSection;
import com.hardware.erp.report.repository.ReportRepository.RateSlabView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CR-086. The only arithmetic the service does: the CGST/SGST/IGST split and folding slabs into rate rows. */
class ReportServiceImplTest {

    private static RateSlabView slab(String rate, boolean inter, long taxable, long gst) {
        return new RateSlabView() {
            @Override public BigDecimal getRatePercent() { return new BigDecimal(rate); }
            @Override public Boolean getInterState() { return inter; }
            @Override public Long getTaxablePaise() { return taxable; }
            @Override public Long getGstPaise() { return gst; }
        };
    }

    @Test
    @DisplayName("an odd paisa of GST goes to SGST, so CGST + SGST always equals the stored total")
    void splitNeverLosesAPaisa() {
        assertThat(ReportServiceImpl.split(1801, false)).containsExactly(900, 901, 0);
        assertThat(ReportServiceImpl.split(1801, true)).containsExactly(0, 0, 1801);
        assertThat(ReportServiceImpl.split(0, false)).containsExactly(0, 0, 0);
    }

    @Test
    @DisplayName("intra- and inter-state slabs of the same rate fold into one row with all three taxes")
    void sectionFoldsSlabsByRate() {
        GstSection s = ReportServiceImpl.section("Outward", List.of(
                slab("18.00", false, 100_000, 18_000),
                slab("18.00", true, 50_000, 9_000),
                slab("5.00", false, 20_000, 1_000)));

        assertThat(s.rows()).hasSize(2);
        assertThat(s.rows().get(0).ratePercent()).isEqualByComparingTo("5");
        var eighteen = s.rows().get(1);
        assertThat(eighteen.ratePercent()).isEqualByComparingTo("18");
        assertThat(eighteen.taxablePaise()).isEqualTo(150_000);
        assertThat(eighteen.cgstPaise()).isEqualTo(9_000);
        assertThat(eighteen.sgstPaise()).isEqualTo(9_000);
        assertThat(eighteen.igstPaise()).isEqualTo(9_000);
        assertThat(eighteen.totalTaxDisplay()).isEqualTo("270.00");
        assertThat(s.totals().taxablePaise()).isEqualTo(170_000);
        assertThat(s.totals().totalTaxPaise()).isEqualTo(28_000);
        assertThat(s.totals().ratePercent()).isNull();
    }
}
