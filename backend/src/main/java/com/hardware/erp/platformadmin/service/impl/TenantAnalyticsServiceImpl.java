package com.hardware.erp.platformadmin.service.impl;

import com.hardware.erp.auth.repository.SecurityAuditLogRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.ChurnPoint;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.GrowthPoint;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.ModuleUsagePoint;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import com.hardware.erp.platformadmin.service.TenantAnalyticsService;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every figure here is a real aggregate query, run at request time - see
 * TenantAnalyticsResponse's own javadoc for the exact derivation and stated
 * limitations of growth/moduleUsage/churn. Twelve small per-month queries
 * for growth/churn is the same "simplicity over cleverness, not a hot path"
 * choice PlatformBillingQueryServiceImpl already made for the Revenue chart.
 */
@Service
@RequiredArgsConstructor
public class TenantAnalyticsServiceImpl implements TenantAnalyticsService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);

    /** table name -> display label. tenant_id is guaranteed on every one of these by CR-016. */
    private static final Map<String, String> MODULE_TABLES = new LinkedHashMap<>();
    static {
        MODULE_TABLES.put("product", "Products");
        MODULE_TABLES.put("customer", "Customers");
        MODULE_TABLES.put("supplier", "Suppliers");
        MODULE_TABLES.put("invoice", "Invoices");
        MODULE_TABLES.put("quotation", "Quotations");
        MODULE_TABLES.put("purchase", "Purchases");
        MODULE_TABLES.put("business_expense", "Expenses");
        MODULE_TABLES.put("worker", "Labour");
    }

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SecurityAuditLogRepository securityAuditLogRepository;
    private final PlatformAuditLogRepository platformAuditLogRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public TenantAnalyticsResponse overview() {
        long activeTenantsNow = tenantRepository.countByStatus(TenantStatus.ACTIVE);

        List<GrowthPoint> growth = new ArrayList<>();
        List<ChurnPoint> churn = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();

        for (int i = 11; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDateTime from = month.atDay(1).atStartOfDay();
            LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
            String label = month.format(MONTH_LABEL);

            long newTenants = tenantRepository.countByCreatedAtBetween(from, to);
            long newUsers = userRepository.countByCreatedAtBetween(from, to);
            long activeUsers = securityAuditLogRepository.countDistinctUsersLoggedInBetween(from, to);
            growth.add(new GrowthPoint(label, newTenants, newUsers, activeUsers));

            long suspended = platformAuditLogRepository.countByActionAndSuccessTrueAndCreatedAtBetween(
                    PlatformAuditAction.TENANT_SUSPENDED, from, to);
            long totalByMonthEnd = tenantRepository.countByCreatedAtBetween(EPOCH, to);
            Double churnRate = totalByMonthEnd == 0 ? null : (suspended * 100.0) / totalByMonthEnd;
            churn.add(new ChurnPoint(label, suspended, totalByMonthEnd, churnRate));
        }

        return new TenantAnalyticsResponse(activeTenantsNow, growth, moduleUsage(activeTenantsNow), churn);
    }

    private List<ModuleUsagePoint> moduleUsage(long activeTenantsNow) {
        List<ModuleUsagePoint> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : MODULE_TABLES.entrySet()) {
            String table = entry.getKey();
            // Table names are a fixed internal constant list (never user input), interpolated because JDBC cannot
            // parameterize an identifier - not a SQL injection surface.
            String sql = "select count(distinct m.tenant_id) from " + table
                    + " m join tenant t on t.tenant_id = m.tenant_id where t.status = 'ACTIVE'";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            long tenantsUsing = count == null ? 0 : count;
            double adoptionPercent = activeTenantsNow == 0 ? 0.0 : (tenantsUsing * 100.0) / activeTenantsNow;
            result.add(new ModuleUsagePoint(entry.getValue(), tenantsUsing, adoptionPercent));
        }
        return result;
    }
}
