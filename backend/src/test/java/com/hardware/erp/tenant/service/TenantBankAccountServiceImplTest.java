package com.hardware.erp.tenant.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.dto.TenantBankAccountRequest;
import com.hardware.erp.tenant.dto.TenantBankAccountResponse;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountStatus;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.mapper.TenantBankAccountMapper;
import com.hardware.erp.tenant.repository.TenantBankAccountQrRepository;
import com.hardware.erp.tenant.repository.TenantBankAccountRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.impl.TenantBankAccountServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantBankAccountServiceImplTest {

    @Mock private TenantBankAccountRepository bankAccountRepository;
    @Mock private TenantBankAccountQrRepository qrRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private SecurityAuditService securityAuditService;

    private TenantBankAccountServiceImpl service;
    private Tenant tenant;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        service = new TenantBankAccountServiceImpl(bankAccountRepository, qrRepository, tenantRepository,
                new TenantBankAccountMapper(), activityLog, securityAuditService);

        tenant = Tenant.builder().id(1L).slug("default").name("Default Shop").status(TenantStatus.ACTIVE).build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(bankAccountRepository.save(any(TenantBankAccount.class))).thenAnswer(inv -> {
            TenantBankAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(nextId++);
            return a;
        });

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private TenantBankAccountRequest request(String label, String accountNumber, boolean defaultAccount) {
        return new TenantBankAccountRequest(label, "HDFC Bank", "Shop Owner", accountNumber,
                "HDFC0001234", "shop@okicici", defaultAccount);
    }

    @Test
    void firstAccountCreatedIsAlwaysDefaultEvenIfNotRequested() {
        when(bankAccountRepository.findByTenantIdAndStatus(1L, TenantBankAccountStatus.ACTIVE))
                .thenReturn(List.of());

        TenantBankAccountResponse response = service.create(request("Primary", "111122223333", false));

        assertThat(response.defaultAccount()).isTrue();
    }

    @Test
    void creatingASecondDefaultAccountUnsetsTheFirst() {
        TenantBankAccount existing = TenantBankAccount.builder().id(1L).tenant(tenant)
                .label("Old Default").bankName("HDFC Bank").accountHolderName("Shop Owner")
                .accountNumber("999900001111").ifscCode("HDFC0009999").defaultAccount(true)
                .status(TenantBankAccountStatus.ACTIVE).qrCodes(new ArrayList<>()).build();
        when(bankAccountRepository.findByTenantIdAndStatus(1L, TenantBankAccountStatus.ACTIVE))
                .thenReturn(new ArrayList<>(List.of(existing)));

        TenantBankAccountResponse response = service.create(request("New Default", "222233334444", true));

        assertThat(response.defaultAccount()).isTrue();
        assertThat(existing.isDefaultAccount()).isFalse();
    }

    @Test
    void rejectsASecondAccountWithTheSameBankAccountNumberAndIfsc() {
        // BUG-class regression: accountNumber is encrypted (non-deterministic ciphertext), so this
        // duplicate check must run over decrypted values in the service, never at the DB level.
        TenantBankAccount existing = TenantBankAccount.builder().id(1L).tenant(tenant)
                .label("Existing").bankName("HDFC Bank").accountHolderName("Shop Owner")
                .accountNumber("111122223333").ifscCode("HDFC0001234").defaultAccount(true)
                .status(TenantBankAccountStatus.ACTIVE).qrCodes(new ArrayList<>()).build();
        when(bankAccountRepository.findByTenantIdAndStatus(1L, TenantBankAccountStatus.ACTIVE))
                .thenReturn(new ArrayList<>(List.of(existing)));

        assertThatThrownBy(() -> service.create(request("Duplicate", "111122223333", false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already saved");
    }

    @Test
    void deletingTheDefaultAccountPromotesAnotherRemainingActiveAccount() {
        TenantBankAccount toDelete = TenantBankAccount.builder().id(1L).tenant(tenant)
                .label("Going away").bankName("HDFC Bank").accountHolderName("Shop Owner")
                .accountNumber("111122223333").ifscCode("HDFC0001234").defaultAccount(true)
                .status(TenantBankAccountStatus.ACTIVE).qrCodes(new ArrayList<>()).build();
        TenantBankAccount other = TenantBankAccount.builder().id(2L).tenant(tenant)
                .label("Staying").bankName("Axis Bank").accountHolderName("Shop Owner")
                .accountNumber("444455556666").ifscCode("UTIB0001234").defaultAccount(false)
                .status(TenantBankAccountStatus.ACTIVE).qrCodes(new ArrayList<>()).build();
        when(bankAccountRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(toDelete));
        when(bankAccountRepository.findByTenantIdAndStatus(1L, TenantBankAccountStatus.ACTIVE))
                .thenReturn(List.of(other));

        service.delete(1L);

        assertThat(toDelete.getStatus()).isEqualTo(TenantBankAccountStatus.INACTIVE);
        assertThat(toDelete.isDefaultAccount()).isFalse();
        assertThat(other.isDefaultAccount()).isTrue();
    }

    @Test
    void revealAccountNumberLogsASecurityAuditEventAndReturnsThePlaintextValue() {
        TenantBankAccount account = TenantBankAccount.builder().id(1L).tenant(tenant)
                .label("Primary").bankName("HDFC Bank").accountHolderName("Shop Owner")
                .accountNumber("111122223333").ifscCode("HDFC0001234").defaultAccount(true)
                .status(TenantBankAccountStatus.ACTIVE).qrCodes(new ArrayList<>()).build();
        when(bankAccountRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(account));

        String revealed = service.revealAccountNumber(1L);

        assertThat(revealed).isEqualTo("111122223333");
        verify(securityAuditService).success(any(), eq(1L), eq("Owner"), anyString(), eq(1L));
    }

    @Test
    void getForAnotherTenantsAccountThrowsNotFound() {
        when(bankAccountRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revealAccountNumber(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
