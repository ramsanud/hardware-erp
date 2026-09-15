package com.hardware.erp.inventory.job;

import com.hardware.erp.analytics.dto.AnalyticsDtos.LowStockPoint;
import com.hardware.erp.analytics.service.impl.AnalyticsServiceImpl;
import com.hardware.erp.inventory.repository.LowStockSnapshotRepository;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.platformadmin.service.JobExecutionTracker;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** CR-084. */
@ExtendWith(MockitoExtension.class)
class LowStockSnapshotJobTest {

    @Mock TenantRepository tenantRepository;
    @Mock StockRepository stockRepository;
    @Mock LowStockSnapshotRepository snapshotRepository;
    @Mock JobExecutionTracker tracker;
    @InjectMocks LowStockSnapshotJob job;

    private static Tenant tenant(long id) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setStatus(TenantStatus.ACTIVE);
        return t;
    }

    @Test
    void oneShopsFailureDoesNotCostTheOthersTheirPoint() {
        when(tracker.start(LowStockSnapshotJob.JOB_NAME)).thenReturn(7L);
        when(tenantRepository.findByStatus(TenantStatus.ACTIVE)).thenReturn(List.of(tenant(1), tenant(2), tenant(3)));
        when(stockRepository.countLowStock(1L)).thenReturn(4L);
        when(stockRepository.countLowStock(2L)).thenThrow(new IllegalStateException("boom"));
        when(stockRepository.countLowStock(3L)).thenReturn(0L);

        job.takeDailySnapshots();

        verify(snapshotRepository).upsert(eq(1L), any(LocalDate.class), eq(4));
        verify(snapshotRepository).upsert(eq(3L), any(LocalDate.class), eq(0));
        verify(snapshotRepository, never()).upsert(eq(2L), any(LocalDate.class), anyInt());
        verify(tracker).success(eq(7L), contains("2 tenants snapshotted, 1 failed"));
        verify(tracker, never()).failure(anyLong(), any());
    }

    @Test
    void summaryReadsAsASentence() {
        assertThat(AnalyticsServiceImpl.lowStockSummary(List.of()))
                .isEqualTo("No low-stock history yet.");
        assertThat(AnalyticsServiceImpl.lowStockSummary(List.of(new LowStockPoint(LocalDate.of(2026, 9, 15), 1))))
                .isEqualTo("1 product low on stock today; history starts here.");
        assertThat(AnalyticsServiceImpl.lowStockSummary(List.of(
                new LowStockPoint(LocalDate.of(2026, 9, 9), 5),
                new LowStockPoint(LocalDate.of(2026, 9, 15), 3))))
                .isEqualTo("3 low on stock today, down 2 over 2 days.");
    }
}
