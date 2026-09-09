package com.hardware.erp.common.activity;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads the business audit trail back (CR-072).
 *
 * Separate from {@link ActivityLogService}, which only writes. Keeping the two
 * apart means no module that records history accidentally acquires the ability
 * to read every other module's - the write interface is injected into roughly
 * ten services, and this one into exactly one controller.
 */
@Service
@RequiredArgsConstructor
public class ActivityLogQueryService {

    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogMapper activityLogMapper;

    /**
     * The tenant is taken from the JWT here and nowhere else. No parameter of
     * this method, and no field of any request reaching it, can influence
     * which shop's history is returned.
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> search(String moduleCode, String entityType,
                                                    Long entityId, Long userId,
                                                    LocalDateTime fromDate, LocalDateTime toDate,
                                                    Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                activityLogRepository.searchForTenant(tenantId, blankToNull(moduleCode),
                        blankToNull(entityType), entityId, userId, fromDate, toDate, pageable),
                activityLogMapper::toResponse);
    }

    /** Populates the module filter with what this shop actually has history for. */
    @Transactional(readOnly = true)
    public List<String> moduleCodes() {
        return activityLogRepository.distinctModuleCodesForTenant(
                SecurityUtils.requireCurrentTenantId());
    }

    /**
     * An empty string arrives from a cleared dropdown and must mean "no
     * filter", not "match the empty module code" - which would return nothing
     * and read as the log being empty.
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
