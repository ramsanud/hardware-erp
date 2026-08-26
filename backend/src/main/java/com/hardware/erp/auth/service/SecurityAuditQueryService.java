package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.SecurityAuditLogResponse;
import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Read side of the security audit log.
 *
 * Deliberately separate from SecurityAuditService, which only writes. Keeping
 * them apart means the write path stays REQUIRES_NEW and dependency-free, and
 * nothing on the read path can accidentally be pulled into an audit write.
 */
public interface SecurityAuditQueryService {

    PageResponse<SecurityAuditLogResponse> search(Long userId, AuditAction action,
                                                  LocalDateTime from, LocalDateTime to,
                                                  Pageable pageable);
}
