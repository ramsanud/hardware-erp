package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.repository.SecurityAuditLogRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import com.hardware.erp.platformadmin.service.impl.TenantAnalyticsServiceImpl;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the aggregation shape and the honest-limitation math (a month with
 * zero tenants must report a null churn rate, never a divide-by-zero or a
 * fabricated 0%) - not the real SQL, which needs Testcontainers/Docker
 * (BUG-ENV-002) and is exercised only by manual live verification here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantAnalyticsServiceImplTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private SecurityAuditLogRepository securityAuditLogRepository;
    @Mock private PlatformAuditLogRepository platformAuditLogRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private TenantAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TenantAnalyticsServiceImpl(
                tenantRepository, userRepository, securityAuditLogRepository, platformAuditLogRepository, jdbcTemplate);

        when(tenantRepository.countByStatus(TenantStatus.ACTIVE)).thenReturn(10L);
        when(tenantRepository.countByCreatedAtBetween(any(), any())).thenReturn(2L);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(5L);
        when(securityAuditLogRepository.countDistinctUsersLoggedInBetween(any(), any())).thenReturn(7L);
        when(platformAuditLogRepository.countByActionAndSuccessTrueAndCreatedAtBetween(
                any(PlatformAuditAction.class), any(), any())).thenReturn(1L);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), eq(Long.class))).thenReturn(3L);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    @Test
    @DisplayName("overview() returns 12 months of growth and churn, and one row per module")
    void overviewShape() {
        TenantAnalyticsResponse response = service.overview();

        assertThat(response.activeTenantsNow()).isEqualTo(10L);
        assertThat(response.growth()).hasSize(12);
        assertThat(response.churn()).hasSize(12);
        assertThat(response.moduleUsage()).hasSize(8);
        assertThat(response.growth().get(11).activeUsers()).isEqualTo(7L);
        assertThat(response.moduleUsage()).allSatisfy(m -> {
            assertThat(m.tenantsUsing()).isEqualTo(3L);
            assertThat(m.adoptionPercent()).isEqualTo(30.0);
        });
    }

    @Test
    @DisplayName("churn rate is a real percentage when tenants exist, computed from real event counts")
    void churnRateComputed() {
        TenantAnalyticsResponse response = service.overview();

        // countByCreatedAtBetween is stubbed to always return 2 regardless of range,
        // so totalTenantsByMonthEnd = 2 and suspended = 1 -> 50%.
        response.churn().forEach(point -> {
            assertThat(point.tenantsSuspended()).isEqualTo(1L);
            assertThat(point.totalTenantsByMonthEnd()).isEqualTo(2L);
            assertThat(point.churnRatePercent()).isEqualTo(50.0);
        });
    }

    @Test
    @DisplayName("churn rate is null, never a fabricated 0%, when no tenants exist yet by month end")
    void churnRateNullWhenNoTenants() {
        when(tenantRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);

        TenantAnalyticsResponse response = service.overview();

        assertThat(response.churn()).allSatisfy(point -> assertThat(point.churnRatePercent()).isNull());
    }
}
