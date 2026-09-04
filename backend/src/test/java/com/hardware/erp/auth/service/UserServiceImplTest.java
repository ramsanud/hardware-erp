package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.*;
import com.hardware.erp.auth.mapper.UserMapper;
import com.hardware.erp.auth.repository.RefreshTokenRepository;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.impl.UserServiceImpl;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
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
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SecurityAuditService auditService;
    @Mock private com.hardware.erp.tenant.service.EntitlementService entitlementService;

    @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    @Spy private UserMapper userMapper = new UserMapper();

    @InjectMocks private UserServiceImpl userService;

    private Role ownerRole;
    private Role staffRole;
    private User owner;
    private User staff;

    @BeforeEach
    void setUp() {
        Permission perm = Permission.builder().id(1L).code(PermissionCode.PRODUCT_VIEW)
                .name("View products").moduleCode("PRODUCT").displayOrder(10).build();

        ownerRole = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE)
                .permissions(new LinkedHashSet<>(Set.of(perm))).build();
        staffRole = Role.builder().id(4L).code("STAFF").name("Staff").systemRole(true)
                .status(RoleStatus.ACTIVE)
                .permissions(new LinkedHashSet<>(Set.of(perm))).build();

        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();

        owner = User.builder().id(1L).tenant(tenant).role(ownerRole).fullName("Saravanan Murugan")
                .mobileNo("9876543210").email("owner@sarahardware.in")
                .passwordHash("hash").status(UserStatus.ACTIVE)
                .tokenVersion(0).failedLoginAttempts(0).build();

        staff = User.builder().id(5L).tenant(tenant).role(staffRole).fullName("Karthik Raja")
                .mobileNo("9843012345").email("karthik@sarahardware.in")
                .passwordHash("hash").status(UserStatus.ACTIVE)
                .tokenVersion(0).failedLoginAttempts(0).build();

        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(roleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(ownerRole));
        when(roleRepository.findByIdAndTenantId(4L, 1L)).thenReturn(Optional.of(staffRole));
        when(userRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(staff));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(owner),
                        null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CreateUserRequest createRequest(String mobile, String email) {
        return new CreateUserRequest("New Employee", mobile, email, "EMP020",
                4L, "Welcome@2026", true);
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("creates a user with a hashed password and forced change")
    void create() {
        UserResponse response = userService.create(
                createRequest("9812345678", "new@sarahardware.in"));

        assertThat(response.fullName()).isEqualTo("New Employee");
        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.roleCode()).isEqualTo("STAFF");
        verify(passwordEncoder).encode("Welcome@2026");
        verify(entitlementService, never()).requireCanAddOwner();
    }

    @Test
    @DisplayName("CR-031: creating an OWNER checks the tenant's owner entitlement limit")
    void createOwnerChecksEntitlement() {
        userService.create(new CreateUserRequest("New Owner", "9812345679",
                "newowner@sarahardware.in", "EMP021", 1L, "Welcome@2026", true));

        verify(entitlementService).requireCanAddOwner();
    }

    @Test
    @DisplayName("CR-031: the owner entitlement limit blocks the create, same as any other BusinessException")
    void createOwnerRejectedWhenAtLimit() {
        org.mockito.Mockito.doThrow(new BusinessException("Your Free plan allows up to 1 owners.",
                        org.springframework.http.HttpStatus.PAYMENT_REQUIRED, "ENTITLEMENT_LIMIT_REACHED"))
                .when(entitlementService).requireCanAddOwner();

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("New Owner", "9812345679",
                "newowner@sarahardware.in", "EMP021", 1L, "Welcome@2026", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ENTITLEMENT_LIMIT_REACHED");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a duplicate mobile number is rejected")
    void duplicateMobile() {
        when(userRepository.existsByMobileNo("9812345678")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                createRequest("9812345678", "new@sarahardware.in")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Mobile number");
    }

    @Test
    @DisplayName("a duplicate email is rejected, case-insensitively")
    void duplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("new@sarahardware.in")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                createRequest("9812345678", "NEW@SaraHardware.in")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email");
    }

    @Test
    @DisplayName("an inactive role cannot be assigned")
    void inactiveRoleRejected() {
        staffRole.setStatus(RoleStatus.INACTIVE);

        assertThatThrownBy(() -> userService.create(
                createRequest("9812345678", "new@sarahardware.in")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactive role");
    }

    @Test
    @DisplayName("a missing role gives 404, not a null pointer")
    void unknownRole() {
        when(roleRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.create(new CreateUserRequest(
                "X", "9812345678", null, null, 99L, "Welcome@2026", false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------- update ----------------

    @Test
    @DisplayName("a role change signs the user out everywhere")
    void roleChangeInvalidatesSessions() {
        userService.update(5L, new UpdateUserRequest("Karthik Raja", "9843012345",
                "karthik@sarahardware.in", "EMP005", 1L, UserStatus.ACTIVE));

        assertThat(staff.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).revokeAllForUser(
                eq(5L), eq(RevokedReason.ROLE_CHANGED), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("deactivation signs the user out everywhere")
    void deactivationInvalidatesSessions() {
        userService.update(5L, new UpdateUserRequest("Karthik Raja", "9843012345",
                "karthik@sarahardware.in", "EMP005", 4L, UserStatus.INACTIVE));

        assertThat(staff.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).revokeAllForUser(
                eq(5L), eq(RevokedReason.USER_DEACTIVATED), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("editing name only does not disturb existing sessions")
    void harmlessEditKeepsSessions() {
        userService.update(5L, new UpdateUserRequest("Karthik Raja S", "9843012345",
                "karthik@sarahardware.in", "EMP005", 4L, UserStatus.ACTIVE));

        assertThat(staff.getTokenVersion()).isZero();
        verify(refreshTokenRepository, never()).revokeAllForUser(anyLong(), any(), any());
    }

    // ---------------- last owner protection ----------------

    @Test
    @DisplayName("the last active owner cannot be moved off the owner role")
    void lastOwnerCannotChangeRole() {
        when(userRepository.lockActiveOwners(1L)).thenReturn(List.of(owner));

        assertThatThrownBy(() -> userService.update(1L, new UpdateUserRequest(
                "Saravanan Murugan", "9876543210", "owner@sarahardware.in",
                "EMP001", 4L, UserStatus.ACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "LAST_OWNER_PROTECTED");
    }

    @Test
    @DisplayName("the last active owner cannot be deactivated")
    void lastOwnerCannotBeDeactivated() {
        when(userRepository.lockActiveOwners(1L)).thenReturn(List.of(owner));

        assertThatThrownBy(() -> userService.update(1L, new UpdateUserRequest(
                "Saravanan Murugan", "9876543210", "owner@sarahardware.in",
                "EMP001", 1L, UserStatus.INACTIVE)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "LAST_OWNER_PROTECTED");
    }

    @Test
    @DisplayName("the last active owner cannot be deleted")
    void lastOwnerCannotBeDeleted() {
        when(userRepository.lockActiveOwners(1L)).thenReturn(List.of(owner));

        assertThatThrownBy(() -> userService.softDelete(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "LAST_OWNER_PROTECTED");
    }

    @Test
    @DisplayName("an owner can be removed while a second active owner exists")
    void secondOwnerAllowsRemoval() {
        User secondOwner = User.builder().id(2L).role(ownerRole).fullName("Lakshmi")
                .mobileNo("9876501234").passwordHash("hash")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        when(userRepository.lockActiveOwners(1L)).thenReturn(List.of(owner, secondOwner));

        userService.softDelete(1L, 5L);

        assertThat(owner.getDeletedAt()).isNotNull();
        assertThat(owner.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("delete is a soft delete: the row survives for created_by references")
    void softDeleteKeepsRow() {
        userService.softDelete(5L, 1L);

        assertThat(staff.getDeletedAt()).isNotNull();
        assertThat(staff.getDeletedBy()).isEqualTo(1L);
        assertThat(staff.getTokenVersion()).isEqualTo(1);
        verify(userRepository, never()).delete(any());
        verify(refreshTokenRepository).revokeAllForUser(
                eq(5L), eq(RevokedReason.USER_DEACTIVATED), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("you cannot delete your own account")
    void cannotDeleteSelf() {
        assertThatThrownBy(() -> userService.softDelete(5L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    @DisplayName("deleting a non-existent user gives 404")
    void deleteUnknown() {
        when(userRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.softDelete(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------- profile / password ----------------

    @Test
    @DisplayName("own-profile edit cannot touch role or status")
    void profileEditIsLimited() {
        UserResponse response = userService.updateOwnProfile(5L,
                new UpdateProfileRequest("Karthik Raja S", "karthik.new@sarahardware.in"));

        assertThat(response.fullName()).isEqualTo("Karthik Raja S");
        assertThat(staff.getRole().getCode()).isEqualTo("STAFF");
        assertThat(staff.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("an admin password reset forces a change and kills sessions")
    void adminResetPassword() {
        userService.resetPassword(5L, new ResetUserPasswordRequest("Temp@2026"));

        assertThat(staff.isMustChangePassword()).isTrue();
        assertThat(staff.getTokenVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches("Temp@2026", staff.getPasswordHash())).isTrue();
    }

    // ---------------------------------------------------------------
    // CR-058 - restoring a soft-deleted account.
    //
    // The whole authorisation check is UserRepository.restoreDeleted's WHERE
    // clause, so these assert on what the service does with its row count:
    // 1 means the guarded update matched; 0 means it did not match for ANY
    // reason (other tenant, never deleted, no such id) and all of those must
    // be reported identically as 404 so ids cannot be probed.
    // ---------------------------------------------------------------

    @Test
    @DisplayName("CR-058: a genuinely deleted account is restored in place and written to the SECURITY audit log")
    void restoresDeletedUser() {
        when(userRepository.restoreDeleted(5L, 1L)).thenReturn(1);

        userService.restore(5L);

        verify(userRepository).restoreDeleted(5L, 1L);
        // Restoring re-enables a login, so it belongs in security_audit_log,
        // never activity_log (CR-015, hard rule 8).
        verify(auditService).success(eq(AuditAction.USER_RESTORED), eq(5L),
                eq(staff.getFullName()), eq("USER"), eq(5L));
    }

    @Test
    @DisplayName("CR-058: restore never revives old sessions - token_version is left where softDelete put it")
    void restoreDoesNotRevivePreviousSessions() {
        staff.softDelete(1L);
        int tokenVersionAfterDelete = staff.getTokenVersion();
        assertThat(tokenVersionAfterDelete).isEqualTo(1);
        when(userRepository.restoreDeleted(5L, 1L)).thenReturn(1);

        userService.restore(5L);

        // Putting token_version back would hand every access token the account
        // still had outstanding its permissions again.
        assertThat(staff.getTokenVersion()).isEqualTo(tokenVersionAfterDelete);
    }

    @Test
    @DisplayName("CR-058: an account that was never deleted gives 404 and writes no audit entry")
    void restoreNotDeletedGives404() {
        when(userRepository.restoreDeleted(5L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> userService.restore(5L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(auditService, never()).success(eq(AuditAction.USER_RESTORED), anyLong(),
                anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("CR-058: another tenant's deleted account cannot be restored")
    void cannotRestoreAnotherTenantsUser() {
        // The signed-in principal belongs to tenant 1; account 5 here is
        // tenant 2's, so the guarded UPDATE matches nothing.
        when(userRepository.restoreDeleted(5L, 1L)).thenReturn(0);
        when(userRepository.restoreDeleted(5L, 2L)).thenReturn(1);

        assertThatThrownBy(() -> userService.restore(5L))
                .isInstanceOf(ResourceNotFoundException.class);

        // The tenant id is taken from the security context, so no caller-
        // supplied value can redirect it at another shop's rows.
        verify(userRepository, never()).restoreDeleted(5L, 2L);
    }

    @Test
    @DisplayName("CR-058: listDeleted only ever asks for the caller's own tenant")
    void listDeletedIsTenantScoped() {
        when(userRepository.findDeletedByTenantId(1L)).thenReturn(List.of());

        assertThat(userService.listDeleted()).isEmpty();

        verify(userRepository).findDeletedByTenantId(1L);
        verify(userRepository, never()).findDeletedByTenantId(2L);
    }
}
