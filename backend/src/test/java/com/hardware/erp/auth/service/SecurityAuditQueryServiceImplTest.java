package com.hardware.erp.auth.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.SecurityAuditLogRepository;
import com.hardware.erp.auth.service.impl.SecurityAuditQueryServiceImpl;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BUG-SEC-001: GET /v1/security-audit-logs never scoped by tenant at all -
 * any caller holding AUDIT_VIEW in any shop could see every other shop's
 * login attempts, IPs, and failure reasons. This asserts the caller's own
 * tenant id is the one actually sent to the repository, not a client
 * parameter and not silently omitted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityAuditQueryServiceImplTest {

    @Mock private SecurityAuditLogRepository auditLogRepository;

    @InjectMocks private SecurityAuditQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(7L).slug("tenant-7").name("Tenant Seven")
                .status(TenantStatus.ACTIVE).build();
        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));

        when(auditLogRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("search always scopes by the caller's own tenant, taken from the authenticated principal")
    void scopesByCallersOwnTenant() {
        service.search(null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Long> tenantIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(auditLogRepository).search(tenantIdCaptor.capture(), any(), any(), any(), any(), any());
        assertThat(tenantIdCaptor.getValue()).isEqualTo(7L);
    }

    @Test
    @DisplayName("an empty result still returns a valid page, not null or an exception")
    void returnsEmptyPageCleanly() {
        PageResponse<?> result = service.search(null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
