package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.RoleRequest;
import com.hardware.erp.auth.dto.RoleResponse;
import com.hardware.erp.auth.entity.*;
import com.hardware.erp.auth.mapper.UserMapper;
import com.hardware.erp.auth.repository.PermissionRepository;
import com.hardware.erp.auth.repository.RefreshTokenRepository;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.impl.RoleServiceImpl;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleServiceImplTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private SecurityAuditService auditService;

    @Spy private UserMapper userMapper = new UserMapper();

    @InjectMocks private RoleServiceImpl roleService;

    private Permission productView;
    private Permission inventoryView;
    private Role ownerRole;
    private Role customRole;

    @BeforeEach
    void setUp() {
        productView = Permission.builder().id(10L).code(PermissionCode.PRODUCT_VIEW)
                .name("View products").moduleCode("PRODUCT").displayOrder(10).build();
        inventoryView = Permission.builder().id(20L).code(PermissionCode.INVENTORY_VIEW)
                .name("View inventory").moduleCode("INVENTORY").displayOrder(10).build();

        ownerRole = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE)
                .permissions(new LinkedHashSet<>(Set.of(productView, inventoryView))).build();

        customRole = Role.builder().id(5L).code("STOCK_CLERK").name("Stock Clerk")
                .systemRole(false).status(RoleStatus.ACTIVE)
                .permissions(new LinkedHashSet<>(Set.of(productView))).build();

        when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
        when(roleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(ownerRole));
        when(roleRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(customRole));
        when(permissionRepository.findByCodeIn(anySet())).thenAnswer(i -> {
            Set<String> codes = i.getArgument(0);
            Set<Permission> found = new LinkedHashSet<>();
            if (codes.contains(PermissionCode.PRODUCT_VIEW)) found.add(productView);
            if (codes.contains(PermissionCode.INVENTORY_VIEW)) found.add(inventoryView);
            return found;
        });
        when(permissionRepository.count()).thenReturn(2L);

        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);

        User authUser = User.builder().id(1L).tenant(tenant).role(ownerRole)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser),
                        null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RoleRequest request(String code, String name, Set<String> permissions,
                                RoleStatus status) {
        return new RoleRequest(code, name, "desc", permissions, status);
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("creates a custom role with resolved permissions")
    void create() {
        RoleResponse response = roleService.create(request("PACKER", "Packer",
                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE));

        assertThat(response.code()).isEqualTo("PACKER");
        assertThat(response.systemRole()).isFalse();
        assertThat(response.permissions()).containsExactly(PermissionCode.PRODUCT_VIEW);
    }

    @Test
    @DisplayName("a duplicate role code is rejected")
    void duplicateCode() {
        when(roleRepository.existsByCodeAndTenantId("PACKER", 1L)).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(request("PACKER", "Packer",
                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Role code");
    }

    @Test
    @DisplayName("a duplicate role name is rejected")
    void duplicateName() {
        when(roleRepository.existsByNameIgnoreCaseAndTenantId("Packer", 1L)).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(request("PACKER", "Packer",
                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Role name");
    }

    @Test
    @DisplayName("an unknown permission code is rejected and named in the message")
    void unknownPermission() {
        assertThatThrownBy(() -> roleService.create(request("PACKER", "Packer",
                Set.of(PermissionCode.PRODUCT_VIEW, "MADE_UP_PERMISSION"), RoleStatus.ACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MADE_UP_PERMISSION");
    }

    // ---------------- update ----------------

    @Test
    @DisplayName("changing permissions signs out every holder of the role")
    void permissionChangeInvalidatesHolders() {
        User holder = User.builder().id(7L).role(customRole).fullName("Dinesh")
                .mobileNo("9843023456").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        when(userRepository.findAllByRoleId(5L)).thenReturn(List.of(holder));

        roleService.update(5L, request("STOCK_CLERK", "Stock Clerk",
                Set.of(PermissionCode.PRODUCT_VIEW, PermissionCode.INVENTORY_VIEW),
                RoleStatus.ACTIVE));

        // A revoked permission must bite now, not when the access token expires.
        assertThat(holder.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).revokeAllForUser(
                eq(7L), eq(RevokedReason.ROLE_CHANGED), any());
    }

    @Test
    @DisplayName("a system role keeps its code")
    void systemRoleCodeImmutable() {
        assertThatThrownBy(() -> roleService.update(1L, request("OWNER_RENAMED", "Owner",
                Set.of(PermissionCode.PRODUCT_VIEW, PermissionCode.INVENTORY_VIEW),
                RoleStatus.ACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("system role cannot be changed");
    }

    @Test
    @DisplayName("the OWNER role cannot lose a permission")
    void ownerKeepsEveryPermission() {
        assertThatThrownBy(() -> roleService.update(1L, request("OWNER", "Owner",
                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.ACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must keep every permission");
    }

    @Test
    @DisplayName("the OWNER role cannot be deactivated")
    void ownerCannotBeDeactivated() {
        assertThatThrownBy(() -> roleService.update(1L, request("OWNER", "Owner",
                Set.of(PermissionCode.PRODUCT_VIEW, PermissionCode.INVENTORY_VIEW),
                RoleStatus.INACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deactivated");
    }

    @Test
    @DisplayName("a role still held by users cannot be deactivated")
    void cannotDeactivateRoleInUse() {
        when(userRepository.countByRoleId(5L)).thenReturn(3L);

        assertThatThrownBy(() -> roleService.update(5L, request("STOCK_CLERK", "Stock Clerk",
                Set.of(PermissionCode.PRODUCT_VIEW), RoleStatus.INACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reassign them");
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("a system role cannot be deleted")
    void systemRoleCannotBeDeleted() {
        assertThatThrownBy(() -> roleService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System roles cannot be deleted");
        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a role still held by users cannot be deleted, and the count is reported")
    void roleInUseCannotBeDeleted() {
        when(userRepository.countByRoleId(5L)).thenReturn(2L);

        assertThatThrownBy(() -> roleService.delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 user(s)");
    }

    @Test
    @DisplayName("an unused custom role is deleted")
    void unusedCustomRoleDeleted() {
        when(userRepository.countByRoleId(5L)).thenReturn(0L);

        roleService.delete(5L);

        verify(roleRepository).delete(customRole);
        verify(auditService).success(eq(AuditAction.ROLE_DELETED), any(), any(),
                eq("ROLE"), eq(5L));
    }

    @Test
    @DisplayName("deleting a non-existent role gives 404")
    void deleteUnknown() {
        when(roleRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> roleService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
