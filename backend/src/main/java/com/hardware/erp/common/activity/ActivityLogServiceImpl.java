package com.hardware.erp.common.activity;

import com.hardware.erp.common.web.RequestCorrelationFilter;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    /**
     * Never written to the log, whatever a caller passes. A history table is
     * read by more people than the tables it describes.
     */
    private static final Set<String> REDACTED = Set.of(
            "password", "passwordHash", "newPassword", "currentPassword",
            "token", "tokenHash", "refreshToken", "accessToken",
            "bankAccountNo", "bankAccountNumber");

    private final ActivityLogWriter activityLogWriter;

    @Override
    public void created(String moduleCode, String entityType, Long entityId,
                        String entityLabel, Map<String, Object> newValues) {
        write(moduleCode, entityType, entityId, entityLabel, ActivityAction.CREATE,
                null, redact(newValues), null);
    }

    /**
     * Diffs before against after and stores only what moved. Storing the whole
     * row would make the log unreadable and would duplicate the table it
     * describes.
     */
    @Override
    public void updated(String moduleCode, String entityType, Long entityId,
                        String entityLabel, Map<String, Object> before,
                        Map<String, Object> after) {
        Map<String, Object> changedFrom = new LinkedHashMap<>();
        Map<String, Object> changedTo = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object oldValue = before.get(entry.getKey());
            if (!Objects.equals(oldValue, entry.getValue())) {
                changedFrom.put(entry.getKey(), oldValue);
                changedTo.put(entry.getKey(), entry.getValue());
            }
        }

        if (changedTo.isEmpty()) {
            // Nothing moved. A row saying "someone pressed Save" is noise.
            return;
        }

        write(moduleCode, entityType, entityId, entityLabel, ActivityAction.UPDATE,
                redact(changedFrom), redact(changedTo), null);
    }

    @Override
    public void deleted(String moduleCode, String entityType, Long entityId,
                        String entityLabel, String remarks) {
        write(moduleCode, entityType, entityId, entityLabel, ActivityAction.DELETE,
                null, null, remarks);
    }

    @Override
    public void action(String moduleCode, String entityType, Long entityId,
                       String entityLabel, ActivityAction action, String remarks) {
        write(moduleCode, entityType, entityId, entityLabel, action, null, null, remarks);
    }

    /**
     * Builds the row and hands it to {@link ActivityLogWriter}, which owns the
     * REQUIRES_NEW boundary. BUG-BE-002: this used to be a protected method
     * carrying the annotation itself and calling itself through {@code this},
     * which never reaches the proxy, so the propagation never applied.
     */
    private void write(String moduleCode, String entityType, Long entityId,
                       String entityLabel, ActivityAction action,
                       Map<String, Object> oldValues, Map<String, Object> newValues,
                       String remarks) {
        Optional<AppUserDetails> current = SecurityUtils.currentUser();
        HttpServletRequest request = currentRequest();
        ActivityLog entry = ActivityLog.builder()
                // CR-072. From the JWT, never a request parameter. Empty for a
                // scheduled job or an import, and that row is then readable by
                // nobody - see V55.
                .tenantId(SecurityUtils.currentTenantId().orElse(null))
                .moduleCode(moduleCode)
                .entityType(entityType)
                .entityId(entityId)
                .entityLabel(truncate(entityLabel, 255))
                .action(action)
                .oldValues(oldValues)
                .newValues(newValues)
                .userId(current.map(AppUserDetails::getId).orElse(null))
                .fullName(current.map(AppUserDetails::getFullName).orElse("SYSTEM"))
                .roleCode(current.map(AppUserDetails::getRoleCode).orElse(null))
                .ipAddress(request != null ? SecurityUtils.clientIp(request) : null)
                .requestId(request != null
                        ? RequestCorrelationFilter.currentRequestId(request) : null)
                .remarks(truncate(remarks, 500))
                .build();

        try {
            activityLogWriter.write(entry);
        } catch (RuntimeException ex) {
            // OUTSIDE the REQUIRES_NEW boundary on purpose - see
            // ActivityLogWriter. The failure has already rolled back the
            // writer's own transaction and nothing else, so the caller's work
            // is intact and this is genuinely safe to swallow.
            //
            // RuntimeException rather than DataAccessException: a flush
            // failure surfaces through the proxy as TransactionSystemException
            // or UnexpectedRollbackException just as often as it does as a
            // DataAccessException, and the contract here is that a history
            // row can never break the sale it describes.
            log.error("ACTIVITY LOG WRITE FAILED tenant={} module={} entity={} id={} action={}",
                    entry.getTenantId(), moduleCode, entityType, entityId, action, ex);
        }
    }

    private Map<String, Object> redact(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        values.forEach((key, value) ->
                safe.put(key, REDACTED.contains(key) ? "***" : value));
        return safe;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
